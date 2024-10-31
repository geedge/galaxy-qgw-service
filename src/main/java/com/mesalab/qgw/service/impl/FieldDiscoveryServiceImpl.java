package com.mesalab.qgw.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.geedgenetworks.utils.StringUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayway.jsonpath.JsonPath;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.DataTypeMapping;
import com.mesalab.common.enums.HttpStatusCodeEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.common.utils.HazelcastInstanceMapUtil;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.qgw.model.basic.DSLQueryRequestParam;
import com.mesalab.qgw.model.basic.EngineConfigSource;
import com.mesalab.qgw.model.basic.QueryCache;
import com.mesalab.qgw.model.basic.SQLQueryContext;
import com.mesalab.qgw.service.DatabaseService;
import com.mesalab.qgw.service.FieldDiscoveryService;
import com.mesalab.qgw.service.SQLSyncQueryService;
import com.mesalab.services.common.enums.MetricFunction;
import com.mesalab.services.common.enums.MetricType;
import com.mesalab.services.configuration.JobConfig;
import com.mesalab.services.service.impl.TaskExecuteService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static com.mesalab.services.service.impl.JobExecuteService.getDecimalFormat;
import static com.mesalab.services.service.impl.JobExecuteService.markJobFailure;

/**
 * TODO
 *
 * @Classname FieldDiscoveryServiceImpl
 * @Date 2024/9/23 14:06
 * @Author wWei
 */
@Service
public class FieldDiscoveryServiceImpl implements FieldDiscoveryService {
    private static final Log log = LogFactory.get();
    private DatabaseService databaseService;
    private Environment environment;
    private EngineConfigSource engineConfigSource;
    private TaskExecuteService taskExecuteService;
    private JobConfig jobCfg;
    private SQLSyncQueryService sqlSyncQueryService;

    private final ThreadPoolTaskExecutor taskThreadPool;

    public FieldDiscoveryServiceImpl(ThreadPoolTaskExecutor taskThreadPool) {
        this.taskThreadPool = taskThreadPool;
    }


    @Override
    public void asyncRun(DSLQueryRequestParam request) throws ExecutionException {
        JobInfo jobInfo = new JobInfo(request.getId());
        String partitionKey = databaseService.getPartitionKey(request.getDataSource());
        List<String> intervals = request.getIntervals();
        String[] split = intervals.get(0).split("/");
        DateTime start = DateUtil.parse(split[0]);
        DateTime end = DateUtil.parse(split[1]);
        Interval interval = new Interval();
        interval.setStart(start.getTime() / 1000);
        interval.setEnd(end.getTime() / 1000);
        try {
            if (jobCfg.isTimeSlicingEnabled() && interval.isValid()) {
                executeSlicingFieldDiscoveryJob(request.getId(), jobInfo, request, interval, partitionKey);
            } else {
                String filter = String.format(buildFilterFormatSql(),
                        request.getFilter(),
                        partitionKey, interval.getStart(), partitionKey, interval.getEnd());
                executeFieldDiscoveryJob(request.getId(), jobInfo, request, filter);
            }
        } catch (InterruptedException e) {
            // 处理 InterruptedException 并保留中断状态
            Thread.currentThread().interrupt();
            markJobFailure(request.getId(), "Job was interrupted: " + e.getMessage());
        }

    }

    @Override
    public void validate(DSLQueryRequestParam request) {
        Object filter = request.getFilter();
        if (StrUtil.isEmptyIfStr(filter)) {
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), "Filter cannot be null or empty."));
        }
        Object dataSource = request.getDataSource();
        if (StrUtil.isEmptyIfStr(dataSource)) {
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), "Data Source cannot be null or empty."));
        }
        List<String> fields = request.getFields();
        if (fields == null || fields.isEmpty()) {
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), "Fields cannot be null or empty."));
        }

        List<String> fieldsDistinct = fields.stream().distinct().collect(Collectors.toList());
        request.setFields(fieldsDistinct);

        List<String> intervals = request.getIntervals();
        if (intervals == null || intervals.size() == 0) {
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), "Intervals cannot be null or empty."));

        }

        Map<String, Object> customRequestParam = request.getCustomRequestParam();
        if (customRequestParam == null || customRequestParam.size() == 0) {
            return;

        }
        Object metrics = customRequestParam.get("custom.statistics.metrics");
        if ((metrics instanceof List) && ((List<Map<String, Object>>) metrics).size() == 1) {
            Map<String, Object> o = ((List<Map<String, Object>>) metrics).get(0);
            Object metric = o.get("metric_name");
            boolean validMetric = MetricType.isValid(String.valueOf(metric));
            Map<String, Object> function = (Map<String, Object>) o.get("function");
            Object funName = function.get("name");
            boolean validFn = MetricFunction.isValid(String.valueOf(funName));
            if (!validMetric || !validFn) {
                throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                        String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), "custom.statistics.metrics.* illegal."));
            }
        }

    }

    private void executeFieldDiscoveryJob(String id, JobInfo jobInfo, DSLQueryRequestParam request, String segmentFilter) throws ExecutionException, InterruptedException {
        if (isCancel(jobInfo.getId())) {
            return;
        }
        String dataSource = request.getDataSource();
        String totalMetric = "count(*)";
        Map<String, Object> customRequestParam = request.getCustomRequestParam();
        Object metric = null;
        Object fn = null;
        if (StringUtil.isNotEmpty(request.getCustomRequestParam()) && StringUtil.isNotEmpty(customRequestParam.get("custom.statistics.metrics"))) {
            Object metrics = customRequestParam.get("custom.statistics.metrics");
            List<Map<String, Object>> metricsList = (List<Map<String, Object>>) metrics;
            Map<String, Object> map = metricsList.get(0);
            metric = map.get("metric_name");
            Map<String, Object> function = (Map<String, Object>) map.get("function");
            fn = function.get("name");
            String measurements = databaseService.getValueByKeyInSchemaDoc(dataSource, "measurements");
            List<Map<String, String>> read = JsonPath.read(measurements, "$.field_discovery_metric['" + metric + "'][?(@.fn == '" + fn + "')]");
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
        List<String> fields = request.getFields();

        for (String field : fields) {
            Object finalMetric = metric;
            Object finalFn = fn;
            taskCallbackList.put(field, CompletableFuture.supplyAsync(() -> taskExecuteService.executeFieldDiscovery(id, request.getDataSource(), field
                    , finalMetric
                    , finalFn
                    , segmentFilter), taskThreadPool));
        }
        waitAsyncResultAndUpdateProcess(id, jobInfo, taskCallbackList, request, map, metric, fn);
    }

    private void executeSlicingFieldDiscoveryJob(String id, JobInfo jobInfo, DSLQueryRequestParam request, Interval interval, String partitionKey) throws ExecutionException, InterruptedException {
        jobInfo.setTotalTimes((int) ((interval.getEnd() - interval.getStart()) / jobCfg.getTimeSlicingInterval() + 1));
        while (true) {
            jobInfo.setCurrentTimes(jobInfo.getCurrentTimes() + 1);
            if (jobInfo.isTimeout()) {
                log.error("execute job timeout: job-{}", jobInfo);
                markJobFailure(id, "execute job timeout");
                break;
            }
            QueryCache queryCache = HazelcastInstanceMapUtil.get(id);
            if (StrUtil.isEmptyIfStr(queryCache)) {
                log.warn("cache expiration: job-{}", jobInfo);
                break;
            }
            Map<String, Object> job = queryCache.getBaseResult().getJob();
            if (BooleanUtil.toBoolean(String.valueOf(job.get(JobConfig.IS_DONE)))
                    || BooleanUtil.toBoolean(String.valueOf(job.get(JobConfig.IS_FAILED)))
                    || BooleanUtil.toBoolean(String.valueOf(job.get(JobConfig.IS_CANCELED)))) {
                log.warn("job done or interrupt: job obj-{}", job);
                break;
            }
            long start = interval.getEnd() - jobCfg.getTimeSlicingInterval();
            if (start > interval.getStart()) {
                interval.setEnd(start);
                String newFilter = String.format(buildFilterFormatSql(),
                        request.getFilter(),
                        partitionKey, interval.getEnd(), partitionKey, interval.getEnd() + jobCfg.getTimeSlicingInterval());
                executeFieldDiscoveryJob(id, jobInfo, request, newFilter);
            } else {
                String where = String.format(buildFilterFormatSql(),
                        request.getFilter(),
                        partitionKey, interval.getStart(), partitionKey, interval.getEnd());
                executeFieldDiscoveryJob(id, jobInfo, request, where);
                break;
            }
        }
    }

    private static String buildFilterFormatSql() {
        return "(%s) AND (%s >= %s AND %s < %s)";
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

    private void waitAsyncResultAndUpdateProcess(String id, JobInfo jobInfo, Map<String, Future<List<Map<String, Object>>>> taskCallbackList, DSLQueryRequestParam request, Map<String, Long> mapCurrent
            , Object metric, Object fn) throws ExecutionException, InterruptedException {
        List<String> updatedResult = Lists.newArrayList();
        while (true) {
            if (jobInfo.isTimeout()) {
                log.error("execute job timeout, job id is: {}", id);
                markJobFailure(id, "execute job timeout");
                taskCallbackList.keySet().forEach(o -> taskCallbackList.get(o).cancel(true));
                break;
            }
            if (updatedResult.size() >= taskCallbackList.size()) {
                break;
            }
            long completedTaskCount = taskCallbackList.keySet().stream().filter(o -> taskCallbackList.get(o).isDone()).count();
            if ((completedTaskCount - updatedResult.size()) * 1.0 / taskCallbackList.size() < 0.01 && completedTaskCount < taskCallbackList.size()) {
                continue;
            }
            log.info("id :{}, done size: {}, task size: {}", id, completedTaskCount, taskCallbackList.size());
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
                if (StringUtil.isNotEmpty(metric)) {
                    String measurements = databaseService.getValueByKeyInSchemaDoc(request.getDataSource(), "measurements");
                    List<Map<String, String>> read = JsonPath.read(measurements, "$.field_discovery_metric['" + metric + "'][?(@.fn == '" + fn + "')]");
                    if (!read.isEmpty()) {
                        fnDefault = fn.toString();
                        value = read.get(0).get("value");
                    }
                }
                Future<List<Map<String, Object>>> listFuture = taskCallbackList.get(field);
                List<Map<String, Object>> data = listFuture.get();
                data.forEach(x -> {
                    for (String k : x.keySet()) {
                        if (k.startsWith(JobConfig.FIELD_DISCOVERY_TOPK_METRIC_PREFIX)) {
                            Object v = x.get(k);
                            x.put(k.replace(JobConfig.FIELD_DISCOVERY_TOPK_METRIC_PREFIX, ""), v);
                            x.remove(k);
                        }
                    }
                });
                QueryCache queryCache = HazelcastInstanceMapUtil.get(id);
                if (StrUtil.isEmptyIfStr(queryCache)) {
                    markJobFailure(id, "Cache expiration.");
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

    private void updateResult(String id, String field, long currentTotalMetric, Object lastTotalMetric, String value, List<Map<String, Object>> topList, double jobCompletedProcess) {
        Map<String, Object> map = Maps.newHashMap();
        map.put("topk", topList);
        map.put("distinct_count", Math.min(topList.size(), 100));
        map.put(value, currentTotalMetric + Long.parseLong(lastTotalMetric.toString()));
        try {
            HazelcastInstanceMapUtil.retrieveMap().lock(id);
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
        } finally {
            HazelcastInstanceMapUtil.retrieveMap().unlock(id);
        }
    }

    private Map<String, Long> getCount(String logType, String filter, String totalMetric) {
        String sql = String.format(Objects.requireNonNull(environment.getProperty("JOB_LOG_COUNT")),
                totalMetric, logType, StrUtil.isBlankIfStr(filter) ? "" : "WHERE ".concat(filter));
        BaseResult baseResult = sqlSyncQueryService.executeQuery(SQLQueryContext.builder().originalSQL(sql).build());
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

    @Autowired
    public void setDatabaseService(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    @Autowired
    public void setJobCfg(JobConfig jobCfg) {
        this.jobCfg = jobCfg;
    }

    @Autowired
    public void setTaskExecuteService(TaskExecuteService taskExecuteService) {
        this.taskExecuteService = taskExecuteService;
    }

    @Autowired
    public void setSqlSyncQueryService(SQLSyncQueryService sqlSyncQueryService) {
        this.sqlSyncQueryService = sqlSyncQueryService;
    }

    @Autowired
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Autowired
    public void setEngineConfigSource(EngineConfigSource engineConfigSource) {
        this.engineConfigSource = engineConfigSource;
    }
}
