package com.mesalab.services.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayway.jsonpath.JsonPath;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.DataTypeMapping;
import com.mesalab.common.enums.DBType;
import com.mesalab.common.enums.QueryOption;
import com.mesalab.common.enums.HttpStatusCodeEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.HazelcastInstanceMapUtil;
import com.mesalab.common.utils.sqlparser.SQLFunctionUtil;
import com.mesalab.common.utils.sqlparser.SQLHelper;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.qgw.model.basic.*;
import com.mesalab.qgw.service.DSLService;
import com.mesalab.qgw.service.SQLSyncQueryService;
import com.mesalab.qgw.service.DatabaseService;
import com.mesalab.services.common.property.SqlPropertySourceFactory;
import com.mesalab.services.configuration.JobConfig;
import com.geedgenetworks.utils.DateUtils;
import com.geedgenetworks.utils.StringUtil;
import lombok.Data;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.ComparisonOperator;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.*;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static com.mesalab.qgw.service.impl.JobServiceImpl.execute;
import static com.mesalab.qgw.service.impl.JobServiceImpl.executeUpdate;

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
    SQLSyncQueryService sqlSyncQueryService;
    @Autowired
    TaskExecuteService taskExecuteService;
    @Autowired
    DatabaseService databaseService;
    @Autowired
    DSLService dslService;
    @Autowired
    EngineConfigSource engineConfigSource;
    @Autowired
    JobConfig jobCfg;

    @Async("jobExecutor")
    public void addExecutorFieldDiscovery(String id, DSLQueryRequestParam request) {
        signJobInfoToStartOnQueryCache(id);
        try {
            JobInfo jobInfo = new JobInfo(id);
            String partitionKey = databaseService.getPartitionKey(request.getDataSource());
            Interval interval = getInterval("SELECT 1 FROM table WHERE " + request.getFilter(), partitionKey);
            if (jobCfg.isTimeSlicingEnabled() && interval.isValid()) {
                executeSlicingFieldDiscoveryJob(id, jobInfo, request, interval, partitionKey);
            } else {
                executeFieldDiscoveryJob(id, jobInfo, request, request.getFilter());
            }
        } catch (Exception e) {
            signJobInfoToFailedOnQueryCache(id);
            log.info("Exec SQL Query Job Error, message is: {}", e.getMessage());
        } finally {
            signJobInfoToDoneOnQueryCache(id);
        }
    }

    @Async("jobExecutor")
    public void addExecutorStatistics(String id, HashMap<String, Object> param, Map<String, Object> property) {
        JobInfo jobInfo = new JobInfo(id);
        try {
            Interval interval = getInterval(param.get(JobConfig.KEY_CUSTOM_STATISTICS_SQL).toString(), databaseService.getPartitionKey(param.get(JobConfig.QUERY_DATA_SOURCE).toString()));
            if (jobCfg.isTimeSlicingEnabled()
                    && interval.isValid()
                    && StringUtil.isNotEmpty(property.get(JobConfig.JOB_PROPERTY_K_OPTION))) {
                executeSlicingStatisticsJob(jobInfo, param, interval);
            } else {
                PlainSelect plainSelectOuter = getWhereOuter(param.get(JobConfig.KEY_CUSTOM_STATISTICS_SQL).toString());
                Expression whereOuter = plainSelectOuter.getWhere();

                PlainSelect plainSelectInner = SQLHelper.getSelectInner(param.get(JobConfig.KEY_CUSTOM_STATISTICS_SQL).toString());
                Expression whereInner = plainSelectInner.getWhere();
                FromItem fromItemInner = plainSelectInner.getFromItem();

                executeLongTerm(jobInfo, param.get(JobConfig.KEY_CUSTOM_STATISTICS_SQL).toString(), fromItemInner.toString(), whereOuter == null ? " 1=1 " : whereOuter.toString(), whereInner == null ? " 1=1 " : whereInner.toString(), false);
            }
        } catch (Exception e) {
            executeUpdate(String.format(Objects.requireNonNull(env.getProperty("JOB_UPDATE_FIELD_VALUE")), JobConfig.DETAIL, JobConfig.IS_FAILED, id, true));
            log.error(e, "execute job error: jobId-{}", id);
        } finally {
            execute(String.format(Objects.requireNonNull(env.getProperty("JOB_UPDATE_PROGRESS")),
                    id, true, 1, System.currentTimeMillis() - jobInfo.getStartTime()));
        }
    }

    @Async("jobExecutor")
    public void addExecutorSql(String id, SqlQueryRequestParam request) {
        signJobInfoToStartOnQueryCache(id);
        try {
            BaseResult baseResult = sqlSyncQueryService.executeQuery(QueryContext.builder()
                    .query(request.getStatement())
                    .option(request.isDryRun() ? QueryOption.SYNTAX_VALIDATION.getValue() : QueryOption.REAL_TIME.getValue())
                    .format(request.getOutputMode().getValue())
                    .build());
            updateJobResultOnQueryCache(id, baseResult);
        } catch (RuntimeException e) {
            signJobInfoToFailedOnQueryCache(id);
            log.info("Exec SQL Query Job Error, message is: {}", e.getMessage());
        } finally {
            signJobInfoToDoneOnQueryCache(id);
        }
    }

    @Async("jobExecutor")
    public void addExecutorDsl(String id, DSLQueryRequestParam request) {
        signJobInfoToStartOnQueryCache(id);
        try {
            DSLProfile dslProfile = BeanUtil.copyProperties(request, DSLProfile.class);
            BaseResult baseResult = dslService.execDsl(dslProfile);
            updateJobResultOnQueryCache(id, baseResult);
        } catch (RuntimeException e) {
            signJobInfoToFailedOnQueryCache(id);
            log.info("Exec SQL Query Job Error, message is: {}", e.getMessage());
        } finally {
            signJobInfoToDoneOnQueryCache(id);
        }
    }

    private static void updateJobResultOnQueryCache(String id, BaseResult baseResult) {
        if (!baseResult.isSuccess()) {
            throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(), baseResult.getMessage()));
        }
        QueryCache queryCache = HazelcastInstanceMapUtil.get(id);
        Map<String, Object> job = queryCache.getBaseResult().getJob();
        baseResult.setJob(job);
        queryCache.setBaseResult(baseResult);
        HazelcastInstanceMapUtil.put(id, queryCache);
    }

    private static void signJobInfoToStartOnQueryCache(String id) {
        QueryCache queryCache = HazelcastInstanceMapUtil.get(id);
        queryCache.getBaseResult().getJob().put(JobConfig.START_TIME, System.currentTimeMillis() / 1000);
        HazelcastInstanceMapUtil.put(id, queryCache);
    }

    private static void signJobInfoToDoneOnQueryCache(String id) {
        QueryCache queryCache = HazelcastInstanceMapUtil.get(id);
        Map<String, Object> job = queryCache.getBaseResult().getJob();
        job.put(JobConfig.IS_DONE, true);
        job.put(JobConfig.DONE_PROGRESS, 1);
        job.put(JobConfig.END_TIME, System.currentTimeMillis() / 1000);
        HazelcastInstanceMapUtil.put(id, queryCache);
    }

    public static void signJobInfoToFailedOnQueryCache(String id) {
        QueryCache queryCache = HazelcastInstanceMapUtil.get(id);
        Map<String, Object> job = queryCache.getBaseResult().getJob();
        job.put(JobConfig.IS_FAILED, true);
        HazelcastInstanceMapUtil.put(id, queryCache);
    }


    private void executeLongTerm(JobInfo jobInfo, String cusSql, String countDataSource, String newFilter, String countFilter, boolean isSecBuild) throws JSQLParserException {
        if (isCancelLongTerm(jobInfo.getId())) {
            return;
        }

        long currentCount = getCount(countDataSource, countFilter, "count(*)").get("logCount");
        if (currentCount == 0) {
            return;
        }
        long count = jobInfo.getCount() + currentCount;
        executeUpdate(String.format(Objects.requireNonNull(env.getProperty("JOB_UPDATE_FIELD_VALUE")), JobConfig.DETAIL, JobConfig.COUNT, jobInfo.getId(), count));
        jobInfo.setCount(count);
        Select parse = (Select) CCJSqlParserUtil.parse("SELECT 1 FROM table WHERE " + newFilter);
        PlainSelect plainSelect = (PlainSelect) parse.getSelectBody();
        Select parseSQL = (Select) CCJSqlParserUtil.parse(cusSql);
        PlainSelect plainSelectSQL = (PlainSelect) parseSQL.getSelectBody();
        plainSelectSQL.setWhere(plainSelect.getWhere());
        Limit limit = plainSelectSQL.getLimit();
        if (isSecBuild && StringUtil.isNotEmpty(limit)) {
            LongValue longValue = new LongValue(Long.parseLong(limit.getRowCount().toString()) * 10);
            limit.setRowCount(longValue.getValue() > 10000 ? longValue : new LongValue(10000));
        }
        List<Future<Boolean>> list = Lists.newArrayList(taskExecuteService.executeLongTerm(jobInfo.getId(), parseSQL.toString()));
        waitAsyncResultAndUpdateProcessLongTerm(jobInfo, list);
    }

    private void executeSlicingFieldDiscoveryJob(String id, JobInfo jobInfo, DSLQueryRequestParam request, Interval interval, String partitionKey) throws ExecutionException, InterruptedException {
        jobInfo.setTotalTimes((int) ((interval.getEnd() - interval.getStart()) / jobCfg.getTimeSlicingInterval() + 1));
        while (true) {
            jobInfo.setCurrentTimes(jobInfo.getCurrentTimes() + 1);
            if (jobInfo.isTimeout()) {
                log.error("execute job timeout: job-{}", jobInfo);
                signJobInfoToFailedOnQueryCache(id);
                break;
            }
            long start = interval.getEnd() - jobCfg.getTimeSlicingInterval();
            if (start > interval.getStart()) {
                interval.setEnd(start);
                String newFilter = String.format("(%s) AND ( %s > %s AND %s <= %s)",
                        request.getFilter(),
                        partitionKey, interval.getEnd(), partitionKey, interval.getEnd() + jobCfg.getTimeSlicingInterval());
                executeFieldDiscoveryJob(id, jobInfo, request, newFilter);
            } else {
                String where = String.format("(%s) AND (%s >= %s AND %s <= %s)",
                        request.getFilter(),
                        partitionKey, interval.getStart(), partitionKey, interval.getEnd());
                executeFieldDiscoveryJob(id, jobInfo, request, where);
                break;
            }
        }
    }

    private void executeSlicingStatisticsJob(JobInfo jobInfo, Map<String, Object> param, Interval interval) throws JSQLParserException {
        String partitionKey = databaseService.getPartitionKey(param.get(JobConfig.QUERY_DATA_SOURCE).toString());
        List<Expression> timeFloorWithFill = SQLHelper.getFunctionParams(param.get(JobConfig.KEY_CUSTOM_STATISTICS_SQL).toString(), SQLFunctionUtil.TIME_FLOOR_WITH_FILL);
        String per = StringUtil.isEmpty(timeFloorWithFill) ? "PT5M" : timeFloorWithFill.get(1).toString().substring(1, timeFloorWithFill.get(1).toString().length() - 1);
        String function = SQLFunctionUtil.translateCommonFun(SQLFunctionUtil.TIME_FLOOR_WITH_FILL + "(" + interval.getEnd() + ", '" + per + "', 'zero')", DBType.CLICKHOUSE.getValue());
        BaseResult baseResult = sqlSyncQueryService.executeQuery(QueryContext.builder().query(StrUtil.format("SELECT {} AS stat_time FROM {} LIMIT 1", function, param.get(JobConfig.QUERY_DATA_SOURCE))).build());
        List<Map<String, Object>> data = (List<Map<String, Object>>) baseResult.getData();
        long statTime = Long.parseLong(data.get(0).get("stat_time").toString()) * 1000;

        List<Date> dateList = Lists.newArrayList(new DateTime(statTime).plus(Period.parse(per)).toDate());
        dateList.add(new Date(statTime));
        dateList.addAll(DateUtils.getLeftDateRange(new Date(statTime), new Date(interval.getStart() * 1000), per));
        //分片粒度 Max(per, TimeSlicingInterval)
        long perSeconds = (dateList.get(0).getTime() - dateList.get(1).getTime()) / 1000;
        long step = jobCfg.getTimeSlicingInterval() / perSeconds == 0 ? 1 : jobCfg.getTimeSlicingInterval() / perSeconds;
        int totalTimes = new Double(Math.ceil((dateList.size() - 1) * 1.0 / step)).intValue();
        jobInfo.setTotalTimes(totalTimes);
        while (true) {
            if (jobInfo.isTimeout()) {
                log.error("execute job timeout: job-{}", jobInfo);
                executeUpdate(String.format(Objects.requireNonNull(env.getProperty("JOB_UPDATE_FIELD_VALUE")), JobConfig.DETAIL, JobConfig.IS_FAILED, jobInfo.getId(), true));
                break;
            }
            int index = (int) (jobInfo.currentTimes * step);
            long start = dateList.get(dateList.size() <= index ? dateList.size() - 1 : index).getTime() / 1000;
            long end = dateList.get((int) (index - step)).getTime() / 1000;
            PlainSelect plainSelect = getWhereOuter(param.get(JobConfig.KEY_CUSTOM_STATISTICS_SQL).toString());
            String newFilter = String.format("(%s) AND ( %s >= %s AND %s < %s)",
                    plainSelect.getWhere(),
                    partitionKey, start, partitionKey, end);
            executeLongTerm(jobInfo, param.get(JobConfig.KEY_CUSTOM_STATISTICS_SQL).toString(), plainSelect.getFromItem().toString(), newFilter, newFilter, true);
            if (start <= interval.getStart()) {
                break;
            }
            jobInfo.setCurrentTimes(jobInfo.getCurrentTimes() + 1);
        }
    }

    private void executeFieldDiscoveryJob(String id, JobInfo jobInfo, DSLQueryRequestParam request, String segmentFilter) throws ExecutionException, InterruptedException {
        if (isCancel(jobInfo.getId())) {
            return;
        }
        String dataSource = request.getDataSource();
        String totalMetric = "count(*)";
        Object metric = request.getCustomRequestParam().get(JobConfig.KEY_CUSTOM_FIELD_DISCOVERY_METRIC);
        if (StringUtil.isNotEmpty(metric)) {
            Object fn = request.getCustomRequestParam().get(JobConfig.KEY_CUSTOM_FIELD_DISCOVERY_METRIC_FN);
            String measurements = databaseService.getValueByKeyInSchemaDoc(dataSource, "measurements");
            List<Map<String, String>> read = JsonPath.read(measurements, "$.aggregates['" + metric + "'][?(@.fn == '" + fn + "')]");
            if (!read.isEmpty()) {
                String column = read.get(0).get("column");
                totalMetric = read.get(0).get("fn") + "(" + column + ")";
            }
        }

        Map<String, Long> map = getCount(dataSource, segmentFilter, totalMetric);
        long currentCount = map.get("logCount");
        if (currentCount == 0) {
            return;
        }
        Map<String, Future<List<Map<String, Object>>>> taskCallbackList = Maps.newHashMap();
        List<String> fields = (List<String>) request.getCustomRequestParam().get(JobConfig.KEY_CUSTOM_FIELD_DISCOVERY_FIELDS);

        for (String field : fields) {
            taskCallbackList.put(field, taskExecuteService.executeFieldDiscovery(id, request.getDataSource(), field
                    , request.getCustomRequestParam().get(JobConfig.KEY_CUSTOM_FIELD_DISCOVERY_METRIC)
                    , request.getCustomRequestParam().get(JobConfig.KEY_CUSTOM_FIELD_DISCOVERY_METRIC_FN)
                    , segmentFilter));
        }
        waitAsyncResultAndUpdateProcess(id, jobInfo, taskCallbackList, request, map);
    }

    private void waitAsyncResultAndUpdateProcess(String id, JobInfo jobInfo, Map<String, Future<List<Map<String, Object>>>> taskCallbackList, DSLQueryRequestParam request, Map<String, Long> mapCurrent) throws ExecutionException, InterruptedException {
        List<String> updatedResult = Lists.newArrayList();
        while (true) {
            if (jobInfo.isTimeout()) {
                log.error("execute job timeout, job id is: {}", id);
                signJobInfoToFailedOnQueryCache(id);
                taskCallbackList.keySet().forEach(o -> taskCallbackList.get(o).cancel(true));
                break;
            }
            if (updatedResult.size() >= taskCallbackList.size()) {
                break;
            }
            long completedTaskCount = taskCallbackList.keySet().stream().filter(o -> taskCallbackList.get(o).isDone()).count();
            DecimalFormat format = getDecimalFormat();
            double jobCompletedProcess = Double.parseDouble(format.format(jobInfo.getCurrentTimes() * 1.0 / jobInfo.getTotalTimes() - ((1 - completedTaskCount * 1.0 / taskCallbackList.size()) / jobInfo.getTotalTimes())));
            List<String> queryDoneField = taskCallbackList.keySet().stream().filter(o -> taskCallbackList.get(o).isDone()).collect(Collectors.toList());

            for (String field : queryDoneField) {
                if (updatedResult.contains(field)) {
                    continue;
                }
                updatedResult.add(field);
                Long currentTotalMetric = mapCurrent.get("totalMetric");
                String fnDefault = "count";
                String value = "count";
                Object metric = request.getCustomRequestParam().get(JobConfig.KEY_CUSTOM_FIELD_DISCOVERY_METRIC);
                Object fn = request.getCustomRequestParam().get(JobConfig.KEY_CUSTOM_FIELD_DISCOVERY_METRIC_FN);
                if (StringUtil.isNotEmpty(metric)) {
                    String measurements = databaseService.getValueByKeyInSchemaDoc(request.getDataSource(), "measurements");
                    List<Map<String, String>> read = JsonPath.read(measurements, "$.aggregates['" + metric + "'][?(@.fn == '" + fn + "')]");
                    if (!read.isEmpty()) {
                        fnDefault = fn.toString();
                        value = read.get(0).get("value");
                    }
                }
                Future<List<Map<String, Object>>> listFuture = taskCallbackList.get(field);
                List<Map<String, Object>> data = listFuture.get();
                QueryCache queryCache = HazelcastInstanceMapUtil.get(id);
                if (StrUtil.isEmptyIfStr(queryCache)) {
                    signJobInfoToFailedOnQueryCache(id);
                    log.error("field_discovery task query lastTopK Error: Cache expiration.");
                    return;
                }
                Object lastTotalMetric = 0;
                if (StringUtil.isNotEmpty(queryCache.getBaseResult().getData())) {
                    List<Map> map = (List<Map>) queryCache.getBaseResult().getData();
                    for (Map map1 : map) {
                        if (CollectionUtil.isNotEmpty(map1) && map1.containsKey(field) && !StrUtil.isEmptyIfStr(map1.get(field))) {
                            Map item = (Map) map1.get(field);
                            List<Map<String, Object>> topk = (List<Map<String, Object>>) item.get("topk");
                            lastTotalMetric = item.get(value);
                            Map<String, String> metrics = Maps.newHashMap();
                            metrics.put(value, "count".equalsIgnoreCase(fnDefault) ? "sum" : fnDefault);
                            data = mergeData(data, topk, Lists.newArrayList("value"), metrics);
                        }

                    }
                }
                data = sortDataAndSetMaxSize(data, value, DataTypeMapping.LONG, false);
                updateResult(id, field, currentTotalMetric, lastTotalMetric, value, data, jobCompletedProcess);

            }
            if (completedTaskCount >= taskCallbackList.size()) {
                break;
            }
        }
    }

    private void updateResult(String id, String field, long currentTotalMetric, Object lastTotalMetric, String value, List<Map<String, Object>> topList, double jobCompletedProcess) {
        Map<String, Object> map = Maps.newHashMap();
        map.put("topk", topList);
        map.put("distinct_count", Math.min(topList.size(), 100));
        map.put(value, currentTotalMetric + Long.parseLong(lastTotalMetric.toString()));
        QueryCache queryCache = HazelcastInstanceMapUtil.get(id);
        BaseResult<Object> baseResult = queryCache.getBaseResult();
        if (jobCompletedProcess < 1) {
            Map<String, Object> job = queryCache.getBaseResult().getJob();
            job.put(JobConfig.DONE_PROGRESS, jobCompletedProcess);
        }

        Object data1 = baseResult.getData();
        if (StrUtil.isEmptyIfStr(data1)) {
            List<Object> list = Lists.newArrayList();
            Map<Object, Object> item = Maps.newLinkedHashMap();
            item.put(field, map);
            list.add(item);
            baseResult.setData(list);
            HazelcastInstanceMapUtil.put(id, queryCache);
            return;
        }
        List<Map<String, Object>> list = (List<Map<String, Object>>) data1;
        for (int i = 0; i < list.size(); i++) {
            Map<String, Object> map1 = list.get(i);
            if (map1.containsKey(field)) {
                map1.put(field, map);
                break;
            }
        }
        if (list.stream().noneMatch(o -> o.containsKey(field))) {
            Map<String, Object> objectObjectHashMap = Maps.newHashMap();
            objectObjectHashMap.put(field, map);
            list.add(objectObjectHashMap);
        }
        HazelcastInstanceMapUtil.put(id, queryCache);
    }

    private List<Map<String, Object>> sortDataAndSetMaxSize(List<Map<String, Object>> data, String sortElement, String sortType, boolean isAsc) {
        if (DataTypeMapping.INT.equalsIgnoreCase(sortType) || DataTypeMapping.LONG.equalsIgnoreCase(sortType)) {
            data.sort(Comparator.comparing(o -> Long.valueOf(StringUtil.isEmpty(o.get(sortElement)) ? Long.MIN_VALUE + "" : o.get(sortElement).toString())));
        } else if (DataTypeMapping.FLOAT.equalsIgnoreCase(sortType) || DataTypeMapping.DOUBLE.equalsIgnoreCase(sortType)) {
            data.sort(Comparator.comparing(o -> Double.valueOf(StringUtil.isEmpty(o.get(sortElement)) ? Long.MIN_VALUE + "" : o.get(sortElement).toString())));
        } else {
            data.sort(Comparator.comparing(o -> String.valueOf(o.get(sortElement))));
        }
        if (!isAsc) {
            Collections.reverse(data);
        }
        return data.size() > engineConfigSource.getMaxCacheNum() ? data.subList(0, engineConfigSource.getMaxCacheNum()) : data;
    }

    private List<Map<String, Object>> mergeData(List<Map<String, Object>> data1, List<Map<String, Object>> date2, List<String> dimensions, Map<String, String> metrics) {
        Collection<Map<String, Object>> data = CollectionUtil.addAll(data1, date2);
        Map<String, Map<String, Object>> result = Maps.newHashMap();
        for (Map<String, Object> datum : data) {
            StringBuilder key = new StringBuilder();
            dimensions.forEach(k -> key.append(datum.get(k)));
            if (!result.containsKey(key.toString())) {
                result.put(key.toString(), datum);
                continue;
            }
            Map<String, Object> item = result.get(key.toString());
            for (String k : metrics.keySet()) {
                String action = metrics.get(k);
                Object o1 = item.get(k);
                Object o2 = datum.get(k);
                if (StringUtil.isEmpty(o1) || StringUtil.isEmpty(o2)) {
                    item.put(k, (StringUtil.isEmpty(o1) && StringUtil.isEmpty(o2)) ? null : StringUtil.isEmpty(o1) ? o2 : o1);
                    continue;
                }
                Number number1 = NumberUtil.parseNumber(o1.toString());
                Number number2 = NumberUtil.parseNumber(o2.toString());
                if (action.equals("sum")) {
                    item.put(k, NumberUtil.add(number1, number2));
                    continue;
                }
                if (NumberUtil.isDouble(o1.toString()) || NumberUtil.isDouble(o2.toString())) {
                    double v1 = Double.parseDouble(number1.toString());
                    double v2 = Double.parseDouble(number2.toString());
                    switch (action) {
                        case "max":
                            item.put(k, NumberUtil.max(v1, v2));
                            break;
                        case "min":
                            item.put(k, NumberUtil.min(v1, v2));
                            break;
                        case "avg":
                            item.put(k, NumberUtil.div(NumberUtil.add(v1, v2), 2));
                            break;
                    }
                } else if (NumberUtil.isLong(o1.toString()) || NumberUtil.isLong(o2.toString())) {
                    long v1 = Long.parseLong(number1.toString());
                    long v2 = Long.parseLong(number2.toString());
                    switch (action) {
                        case "max":
                            item.put(k, NumberUtil.max(v1, v2));
                            break;
                        case "min":
                            item.put(k, NumberUtil.min(v1, v2));
                            break;
                        case "avg":
                            item.put(k, NumberUtil.div(NumberUtil.add(v1, v2), 2));
                            break;
                    }
                } else {
                    log.error("task merge data error, data is: {}, {}", data1, date2);
                    throw new BusinessException("task merge data error.");
                }
            }
        }
        return new ArrayList<>(result.values());
    }

    private boolean isCancel(String id) {
        QueryCache queryCache = HazelcastInstanceMapUtil.get(id);
        if (queryCache == null) {
            log.info("Can't find the cache, job id is: {}", id);
            return true;
        }
        long currentTime = System.currentTimeMillis();
        if (currentTime - queryCache.getLatestQueryTimeMs() > jobCfg.getInteractiveTimeout()) {
            queryCache.getBaseResult().getJob().put(JobConfig.IS_CANCELED, true);
            HazelcastInstanceMapUtil.put(id, queryCache);
            log.info("Interactive timeout, job id is: {}", id);
            return true;
        }
        return false;
    }

    @Deprecated
    private boolean isCancelLongTerm(String id) {
        QueryContext build = QueryContext.builder().query(String.format(Objects.requireNonNull(env.getProperty("FIELD_RESULT")), JobConfig.DETAIL, JobConfig.LAST_QUERY_TIME, id)).
                option(QueryOption.REAL_TIME.getValue()).build();
        BaseResult baseResult = sqlSyncQueryService.executeQuery(build);
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

    @Deprecated
    private void waitAsyncResultAndUpdateProcessLongTerm(JobInfo jobInfo, List<Future<Boolean>> taskList) {
        double taskCompletedProcess = 0;
        while (true) {
            if (jobInfo.isTimeout()) {
                log.error("execute job timeout: job-{}", jobInfo);
                executeUpdate(String.format(Objects.requireNonNull(env.getProperty("JOB_UPDATE_FIELD_VALUE")), JobConfig.DETAIL, JobConfig.IS_FAILED, jobInfo.getId(), true));
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


    private Map<String, Long> getCount(String logType, String filter, String totalMetric) {
        String sql = String.format(Objects.requireNonNull(env.getProperty("JOB_LOG_COUNT")),
                totalMetric, logType, StrUtil.isBlankIfStr(filter) ? "" : "WHERE ".concat(filter));
        BaseResult baseResult = sqlSyncQueryService.executeQuery(QueryContext.builder().query(sql).build());
        if (!baseResult.isSuccess()) {
            log.error("Job-get log count failed, message: {}", baseResult.getMessage());
            throw new QGWBusinessException(baseResult.getStatus(), baseResult.getCode(), "Job-get log count failed, message: " + baseResult.getMessage());
        }
        List<Map<String, Object>> data = (List<Map<String, Object>>) baseResult.getData();
        Map<String, Long> map = Maps.newHashMap();
        map.put("logCount", Long.parseLong(data.get(0).get("logCount").toString()));
        map.put("totalMetric", Long.parseLong(data.get(0).get("totalMetric").toString()));
        return map;
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

    private PlainSelect getWhereOuter(String sql) throws JSQLParserException {
        Select parse = (Select) CCJSqlParserUtil.parse(sql);
        return (PlainSelect) parse.getSelectBody();
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
        return Long.parseLong(SQLFunctionUtil.getQueryValue("toUnixTimestamp(" + str + ")", DBType.CLICKHOUSE.getValue()));
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
    class JobInfo implements Serializable {
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
