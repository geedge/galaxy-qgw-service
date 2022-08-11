package com.mesalab.services.service.impl;


import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.enums.DBTypeEnum;
import com.mesalab.common.enums.QueryOptionEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.sqlparser.SQLFunctionUtil;
import com.mesalab.common.utils.sqlparser.SQLHelper;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.service.ApiService;
import com.mesalab.qgw.service.MetadataService;
import com.mesalab.services.common.property.SqlPropertySourceFactory;
import com.mesalab.services.configuration.JobConfig;
import com.zdjizhi.utils.DateUtils;
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
import org.joda.time.DateTime;
import org.joda.time.Period;
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
    public void addExecutorFieldDiscovery(String id, HashMap<String, Object> param) {
        Stopwatch watch = Stopwatch.createStarted();
        try {
            JobInfo jobInfo = new JobInfo(id);
            String partitionKey = metadataService.getPartitionKey(param.get(JobConfig.QUERY_DATA_SOURCE).toString());
            Interval interval = getInterval("SELECT 1 FROM table WHERE " + param.get(JobConfig.KEY_CUSTOM_FIELD_DISCOVERY_FILTER).toString(), partitionKey);
            if (jobCfg.isTimeSlicingEnabled() && interval.isValid()) {
                executeSlicingFieldDiscoveryJob(jobInfo, param, interval, partitionKey);
            } else {
                executeFieldDiscoveryJob(jobInfo, param, param.get(JobConfig.KEY_CUSTOM_FIELD_DISCOVERY_FILTER).toString());
            }
        } catch (Exception e) {
            executeUpdate(String.format(Objects.requireNonNull(env.getProperty("JOB_UPDATE_FIELD_VALUE")), JobConfig.DETAIL, JobConfig.IS_ERROR, id, true));
            log.error(e, "execute job error: jobId-{}", id);
        } finally {
            execute(String.format(Objects.requireNonNull(env.getProperty("JOB_UPDATE_PROGRESS")),
                    id, true, 1, watch.elapsed(TimeUnit.MILLISECONDS)));
        }
    }

    @Async("jobExecutor")
    public void addExecutorStatistics(String id, HashMap<String, Object> param) {
        JobInfo jobInfo = new JobInfo(id);
        try {
            executeStatistics(jobInfo, param);
        } catch (Exception e) {
            executeUpdate(String.format(Objects.requireNonNull(env.getProperty("JOB_UPDATE_FIELD_VALUE")), JobConfig.DETAIL, JobConfig.IS_ERROR, id, true));
            log.error(e, "execute job error: jobId-{}", id);
        } finally {
            execute(String.format(Objects.requireNonNull(env.getProperty("JOB_UPDATE_PROGRESS")),
                    id, true, 1, System.currentTimeMillis() - jobInfo.getStartTime()));
        }
    }

    private void executeStatistics(JobInfo jobInfo, Map<String, Object> param) throws JSQLParserException {
        long count = getCount(param.get(JobConfig.QUERY_DATA_SOURCE).toString(), getWhere(param.get(JobConfig.KEY_CUSTOM_STATISTICS_SQL).toString()).toString());
        if (count == 0 || isCancel(jobInfo.getId())) {
            return;
        }
        executeUpdate(String.format(Objects.requireNonNull(env.getProperty("JOB_UPDATE_FIELD_VALUE")), JobConfig.DETAIL, JobConfig.COUNT, jobInfo.getId(), count));
        jobInfo.setCount(count);
        List<Future<Boolean>> list = Lists.newArrayList(taskExecuteService.executeStatistics(jobInfo.getId(), param.get(JobConfig.KEY_CUSTOM_STATISTICS_SQL).toString()));
        waitAsyncResultAndUpdateProcess(jobInfo, list);
    }

    private void executeSlicingFieldDiscoveryJob(JobInfo jobInfo, Map<String, Object> param, Interval interval, String partitionKey) {
        jobInfo.setTotalTimes((int) ((interval.getEnd() - interval.getStart()) / jobCfg.getTimeSlicingInterval() + 1));
        while (true) {
            jobInfo.setCurrentTimes(jobInfo.getCurrentTimes() + 1);
            if (jobInfo.isTimeout()) {
                log.error("execute job timeout: job-{}", jobInfo);
                executeUpdate(String.format(Objects.requireNonNull(env.getProperty("JOB_UPDATE_FIELD_VALUE")), JobConfig.DETAIL, JobConfig.IS_ERROR, jobInfo.getId(), true));
                break;
            }
            long start = interval.getEnd() - jobCfg.getTimeSlicingInterval();
            if (start > interval.getStart()) {
                interval.setEnd(start);
                String newFilter = String.format("(%s) AND ( %s > %s AND %s <= %s)",
                        param.get(JobConfig.KEY_CUSTOM_FIELD_DISCOVERY_FILTER),
                        partitionKey, interval.getEnd(), partitionKey, interval.getEnd() + jobCfg.getTimeSlicingInterval());
                executeFieldDiscoveryJob(jobInfo, param, newFilter);
            } else {
                String where = String.format("(%s) AND (%s >= %s AND %s <= %s)",
                        param.get(JobConfig.KEY_CUSTOM_FIELD_DISCOVERY_FILTER),
                        partitionKey, interval.getStart(), partitionKey, interval.getEnd());
                executeFieldDiscoveryJob(jobInfo, param, where);
                break;
            }
        }
    }

    private void executeFieldDiscoveryJob(JobInfo jobInfo, Map<String, Object> param, String newFilter) {
        long currentCount = getCount(param.get(JobConfig.QUERY_DATA_SOURCE).toString(), newFilter);
        if (currentCount == 0 || isCancel(jobInfo.getId())) {
            return;
        }
        long count = jobInfo.getCount() + currentCount;
        executeUpdate(String.format(Objects.requireNonNull(env.getProperty("JOB_UPDATE_FIELD_VALUE")), JobConfig.DETAIL, JobConfig.COUNT, jobInfo.getId(), count));
        jobInfo.setCount(count);
        List<Future<Boolean>> list = Lists.newArrayList();
        List<String> fields = (List<String>) param.get(JobConfig.KEY_CUSTOM_FIELD_DISCOVERY_FIELDS);
        for (String field : fields) {
            list.add(taskExecuteService.executeFieldDiscovery(jobInfo.getId(), param.get(JobConfig.QUERY_DATA_SOURCE).toString(), newFilter, field, currentCount));
        }
        waitAsyncResultAndUpdateProcess(jobInfo, list);
    }

    private void waitAsyncResultAndUpdateProcess(JobInfo jobInfo, List<Future<Boolean>> taskList) {
        double taskCompletedProcess = 0;
        while (true) {
            if (jobInfo.isTimeout()) {
                log.error("execute job timeout: job-{}", jobInfo);
                executeUpdate(String.format(Objects.requireNonNull(env.getProperty("JOB_UPDATE_FIELD_VALUE")), JobConfig.DETAIL, JobConfig.IS_ERROR, jobInfo.getId(), true));
                taskList.forEach(o -> o.cancel(true));
                break;
            }
            long completedTaskCount = taskList.stream().filter(Future::isDone).count();
            DecimalFormat format = getDecimalFormat();

            double jobCompletedProcess = Double.parseDouble(format.format(jobInfo.getCurrentTimes() * 1.0 / jobInfo.getTotalTimes() - ((1 - completedTaskCount * 1.0 / taskList.size()) / jobInfo.getTotalTimes())));
            if (jobCompletedProcess - taskCompletedProcess >= 0.1 && jobCompletedProcess < 1) {
                taskCompletedProcess = jobCompletedProcess;
                execute(String.format(Objects.requireNonNull(env.getProperty("JOB_UPDATE_PROGRESS")),
                        jobInfo.getId(), false, jobCompletedProcess, System.currentTimeMillis() - jobInfo.startTime));
            }
            if (completedTaskCount >= taskList.size()) {
                break;
            }
        }
    }

    private boolean isCancel(String id) {
        ApiParam build = ApiParam.builder().query(String.format(Objects.requireNonNull(env.getProperty("FIELD_RESULT")), JobConfig.DETAIL, JobConfig.LAST_QUERY_TIME, id)).
                option(QueryOptionEnum.REAL_TIME.getValue()).build();
        BaseResult baseResult = apiService.executeQuery(build);
        if (baseResult.isSuccess() && (StringUtil.isNotEmpty(baseResult.getData()))) {
            List<Map<String, Object>> list = (List<Map<String, Object>>) baseResult.getData();
            long statTime = Long.parseLong(list.get(0).get("value").toString());
            if ((System.currentTimeMillis() - statTime) > jobCfg.getInteractiveTimeout()) {
                executeUpdate(String.format(Objects.requireNonNull(env.getProperty("JOB_UPDATE_FIELD_VALUE")), JobConfig.DETAIL, JobConfig.IS_CANCELED, id, true));
                return true;
            }
        }
        return false;
    }

    private long getCount(String logType, String filter) {
        String sql = String.format(Objects.requireNonNull(env.getProperty("JOB_LOG_COUNT")),
                logType, StrUtil.isBlankIfStr(filter) ? "" : "WHERE ".concat(filter));
        BaseResult baseResult = apiService.executeQuery(ApiParam.builder().query(sql).build());
        if (!baseResult.isSuccess()) {
            log.error("Job-get log count failed, message: {}", baseResult.getMessage());
            throw new BusinessException(baseResult.getStatus(), baseResult.getCode(), "Job-get log count failed, message: " + baseResult.getMessage());
        }
        List<Map<String, Object>> data = (List<Map<String, Object>>) baseResult.getData();
        Object count = data.get(0).get("count");
        return Long.parseLong(count.toString());
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

    private Interval getInterval(String sql, String partitionKey) throws JSQLParserException {
        Select parse = (Select) CCJSqlParserUtil.parse(sql);
        PlainSelect plainSelect = (PlainSelect) parse.getSelectBody();
        Expression where = plainSelect.getWhere();
        List<Expression> list = Lists.newArrayList();
        Interval interval = new Interval();
        if (!isOrExpression(where, list)) {
            parseInterval(list, interval, partitionKey);
        }
        return interval;
    }

    private Expression getWhere(String sql) throws JSQLParserException {
        Select parse = (Select) CCJSqlParserUtil.parse(sql);
        PlainSelect plainSelect = (PlainSelect) parse.getSelectBody();
        Expression where = plainSelect.getWhere();
        return where;
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
                        } else {
                            interval.setStart(getCriticalValue(comparison.getRightExpression().toString()));
                        }
                    } else if (comparison.getStringExpression().matches("<=|<")) {
                        if (comparison.getRightExpression() instanceof LongValue) {
                            interval.setEnd(Long.parseLong(comparison.getRightExpression().toString()));
                        } else {
                            interval.setEnd(getCriticalValue(comparison.getRightExpression().toString()));
                        }
                    }
                }
                if (partitionKey.equals(comparison.getRightExpression().toString())) {
                    if (comparison.getStringExpression().matches(">=|>")) {
                        if (comparison.getLeftExpression() instanceof LongValue) {
                            interval.setEnd(Long.parseLong(comparison.getLeftExpression().toString()));
                        } else {
                            interval.setEnd(getCriticalValue(comparison.getLeftExpression().toString()));
                        }
                    } else if (comparison.getStringExpression().matches("<=|<")) {
                        if (comparison.getLeftExpression() instanceof LongValue) {
                            interval.setStart(Long.parseLong(comparison.getLeftExpression().toString()));
                        } else {
                            interval.setStart(getCriticalValue(comparison.getLeftExpression().toString()));
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

    private long getCriticalValue(String str) {
        return Long.parseLong(SQLFunctionUtil.getQueryValue("toUnixTimestamp(" + str + ")", DBTypeEnum.CLICKHOUSE.getValue()));
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
        private long startTime = System.currentTimeMillis();
        private long count = 0;
        private int totalTimes = 1;
        private int currentTimes = 1;

        public JobInfo(String id) {
            this.id = id;
        }

        public boolean isTimeout() {
            return System.currentTimeMillis() - startTime > jobCfg.getExecutionTimeout();
        }
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.env = environment;
    }
}
