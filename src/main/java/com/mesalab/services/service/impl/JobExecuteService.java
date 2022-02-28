package com.mesalab.services.service.impl;


import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.enums.QueryOptionEnum;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.service.ApiService;
import com.mesalab.qgw.service.MetadataService;
import com.mesalab.services.common.property.SqlPropertySourceFactory;
import com.mesalab.services.configuration.JobConfig;
import com.zdjizhi.utils.StringUtil;
import lombok.Data;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.ComparisonOperator;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.mesalab.services.service.impl.JobServiceImp.execute;
import static com.mesalab.services.service.impl.JobServiceImp.executeUpdate;

/**
 * TODO
 *
 * @Classname TaskService
 * @Date 2021/11/25 10:11 下午
 * @Author wWei
 */
@Service("jobExecuteService")
@PropertySource(value = "classpath:job-sql-template.sql", factory = SqlPropertySourceFactory.class)
public class JobExecuteService implements EnvironmentAware {
    private static final Log log = LogFactory.get();
    @Autowired
    Environment env;
    @Autowired
    ApiService apiService;
    @Autowired
    TaskExecuteService taskExecuteService;
    @Autowired
    MetadataService metadataService;
    @Autowired
    JobConfig jobCfg;

    @Async("jobExecutor")
    public void addExecutor(String id, HashMap<String, Object> param) {
        Stopwatch watch = Stopwatch.createStarted();
        try {
            JobInfo jobInfo = new JobInfo(id);
            String partitionKey = metadataService.getPartitionKey(param.get(JobConfig.QUERY_DATA_SOURCE).toString());
            Interval interval = getInterval(param.get(JobConfig.CUSTOM_FIELD_DISCOVERY_FILTER).toString(), partitionKey);
            if (jobCfg.isTimeSlicingEnabled() && interval.isValid()) {
                executeSlicingJob(jobInfo, watch, param, interval, partitionKey);
            } else {
                executeJob(jobInfo, watch, param, param.get(JobConfig.CUSTOM_FIELD_DISCOVERY_FILTER).toString());
            }
        } catch (Exception e) {
            executeUpdate(String.format(Objects.requireNonNull(env.getProperty("JOB_UPSERT_DETAIL")), JobConfig.IS_ERROR, id, true));
            log.error(e, "execute job error: jobId-{}", id);
        } finally {
            execute(String.format(Objects.requireNonNull(env.getProperty("JOB_UPSET_PROGRESS")),
                    id, true, 1, watch.elapsed(TimeUnit.MILLISECONDS)));
        }
    }

    private void executeSlicingJob(JobInfo jobInfo, Stopwatch watch, Map<String, Object> param, Interval interval, String partitionKey) {
        jobInfo.setTotalTimes((int) ((interval.getEnd() - interval.getStart()) / jobCfg.getTimeSlicingInterval() + 1));
        while (true) {
            jobInfo.setCurrentTimes(jobInfo.getCurrentTimes() + 1);
            if (watch.elapsed(TimeUnit.MILLISECONDS) > jobCfg.getExecutionTimeout()) {
                log.error("execute job timeout: job-{}", jobInfo);
                break;
            }
            long start = interval.getEnd() - jobCfg.getTimeSlicingInterval();
            if (start > interval.getStart()) {
                interval.setEnd(start);
                String newFilter = String.format("(%s) AND ( %s > %s AND %s <= %s)",
                        param.get(JobConfig.CUSTOM_FIELD_DISCOVERY_FILTER),
                        partitionKey, interval.getEnd(), partitionKey, interval.getEnd() + jobCfg.getTimeSlicingInterval());
                executeJob(jobInfo, watch, param, newFilter);
            } else {
                String where = String.format("(%s) AND (%s >= %s AND %s <= %s)",
                        param.get(JobConfig.CUSTOM_FIELD_DISCOVERY_FILTER),
                        partitionKey, interval.getStart(), partitionKey, interval.getEnd());
                executeJob(jobInfo, watch, param, where);
                break;
            }
        }
    }

    private void executeJob(JobInfo jobInfo, Stopwatch watch, Map<String, Object> param, String newFilter) {
        long currentCount = getCount(param.get(JobConfig.QUERY_DATA_SOURCE).toString(), newFilter);
        if (currentCount == 0 || isCancel(jobInfo.getId())) {
            return;
        }
        long count = jobInfo.getCount() + currentCount;
        executeUpdate(String.format(Objects.requireNonNull(env.getProperty("JOB_UPSERT_DETAIL")),
                "count", jobInfo.getId(), count));
        jobInfo.setCount(count);
        List<Future<Boolean>> list = Lists.newArrayList();
        List<String> fields = (List<String>) param.get(JobConfig.CUSTOM_FIELD_DISCOVERY_FIELDS);
        for (String field : fields) {
            list.add(taskExecuteService.execute(jobInfo.getId(), param.get(JobConfig.QUERY_DATA_SOURCE).toString(), newFilter, field, currentCount));
        }
        updateProcess(jobInfo, watch, fields, list);
    }

    private void updateProcess(JobInfo jobInfo, Stopwatch watch, List<String> fields, List<Future<Boolean>> list) {
        double process = 0.0;
        while (true) {
            if (watch.elapsed(TimeUnit.MILLISECONDS) > jobCfg.getExecutionTimeout()) {
                list.forEach(o -> o.cancel(true));
                break;
            }
            int doneNum = list.parallelStream().filter(Future::isDone).toArray().length;
            DecimalFormat format = getDecimalFormat();
            double v = Double.parseDouble(format.format(jobInfo.getCurrentTimes() * 1.0 / jobInfo.getTotalTimes() -
                    ((1 - doneNum * 1.0 / fields.size()) / jobInfo.getTotalTimes())));
            if (v - process >= 0.1 && v < 1) {
                process = v;
                execute(String.format(Objects.requireNonNull(env.getProperty("JOB_UPSET_PROGRESS")),
                        jobInfo.getId(), doneNum == fields.size(), v, watch.elapsed(TimeUnit.MILLISECONDS)));
            }
            if (doneNum >= fields.size()) {
                break;
            }
        }
    }

    private boolean isCancel(String id) {
        ApiParam build = ApiParam.builder().query(String.format(Objects.requireNonNull(env.getProperty("JOB_TTL")), id)).
                option(QueryOptionEnum.REAL_TIME.getValue()).build();
        BaseResult baseResult = apiService.executeQuery(build);
        if (baseResult.isSuccess() && (StringUtil.isNotEmpty(baseResult.getData()))) {
            List<Map<String, Object>> list = (List<Map<String, Object>>) baseResult.getData();
            long statTime = Long.parseLong(list.get(0).get("stat_time").toString());
            if ((System.currentTimeMillis() - statTime) > jobCfg.getInteractiveTimeout()) {
                executeUpdate(String.format(Objects.requireNonNull(env.getProperty("JOB_UPSERT_DETAIL")), "is_canceled", id, true));
                return true;
            }
        }
        return false;
    }

    private Long getCount(String logType, String filter) {
        String sql = String.format(Objects.requireNonNull(env.getProperty("JOB_LOG_COUNT")),
                logType, StrUtil.isBlankIfStr(filter) ? "" : "WHERE ".concat(filter));
        BaseResult baseResult = apiService.executeQuery(ApiParam.builder().query(sql).build());
        if (baseResult.isSuccess()) {
            List<Map<String, Object>> data = (List<Map<String, Object>>) baseResult.getData();
            Object count = data.get(0).get("count");
            return Long.parseLong(count.toString());
        }
        return 0L;
    }

    private DecimalFormat getDecimalFormat() {
        DecimalFormat format = new DecimalFormat("#0.####");
        format.applyPattern("0.0000");
        format.setRoundingMode(RoundingMode.FLOOR);
        return format;
    }

    private boolean isOrExpression(Expression expression, List<Expression> list) {
        if (expression instanceof OrExpression) {
            return true;
        }
        if (expression instanceof AndExpression) {
            AndExpression and = (AndExpression) expression;
            list.add(and.getRightExpression());
            return isOrExpression(and.getLeftExpression(), list);
        }
        list.add(expression);
        return false;
    }

    private Interval getInterval(String filter, String partitionKey) throws JSQLParserException {
        Select parse = (Select) CCJSqlParserUtil.parse("SELECT * FROM table WHERE " + filter);
        PlainSelect plainSelect = (PlainSelect) parse.getSelectBody();
        Expression where = plainSelect.getWhere();
        List<Expression> list = Lists.newArrayList();
        Interval interval = new Interval();
        if (!isOrExpression(where, list)) {
            parseInterval(list, interval, partitionKey);
        }
        return interval;
    }


    private Interval parseInterval(List<Expression> list, Interval interval, String partitionKey) {
        if (interval.isValid()) {
            return interval;
        }
        for (Expression expression : list) {
            if (expression instanceof ComparisonOperator) {
                ComparisonOperator comparison = (ComparisonOperator) expression;
                if (partitionKey.equals(comparison.getLeftExpression().toString())) {
                    if (comparison.getStringExpression().matches(">=|>")) {
                        if (comparison.getRightExpression() instanceof LongValue) {
                            interval.setStart(Long.parseLong(comparison.getRightExpression().toString()));
                        }
                    } else if (comparison.getStringExpression().matches("<=|<")) {
                        if (comparison.getRightExpression() instanceof LongValue) {
                            interval.setEnd(Long.parseLong(comparison.getRightExpression().toString()));
                        }
                    }
                }
                if (partitionKey.equals(comparison.getRightExpression().toString())) {
                    if (comparison.getStringExpression().matches(">=|>")) {
                        if (comparison.getLeftExpression() instanceof LongValue) {
                            interval.setEnd(Long.parseLong(comparison.getLeftExpression().toString()));
                        }
                    } else if (comparison.getStringExpression().matches("<=|<")) {
                        if (comparison.getLeftExpression() instanceof LongValue) {
                            interval.setStart(Long.parseLong(comparison.getLeftExpression().toString()));
                        }
                    }
                }
            } else if (expression instanceof Parenthesis) {
                Parenthesis parenthesis = (Parenthesis) expression;
                List<Expression> expressionList = com.clearspring.analytics.util.Lists.newArrayList();
                if (!isOrExpression(parenthesis.getExpression(), expressionList)) {
                    parseInterval(expressionList, interval, partitionKey);
                }
            }
        }
        return interval;
    }

    @Data
    class Interval {
        private Long start;
        private Long end;

        boolean isValid() {
            return StringUtil.isNotEmpty(this.start)
                    && StringUtil.isNotEmpty(this.end)
                    && this.end - this.start > jobCfg.getTimeSlicingInterval();
        }
    }

    @Data
    class JobInfo {
        private String id;
        private long count = 0;
        private int totalTimes = 1;
        private int currentTimes = 1;
        public JobInfo(String id) {
            this.id = id;
        }
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.env = environment;
    }
}
