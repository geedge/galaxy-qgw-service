package com.mesalab.qgw.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.geedgenetworks.utils.DateUtils;
import com.geedgenetworks.utils.StringUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jfinal.plugin.activerecord.Db;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.*;
import com.mesalab.common.utils.HazelcastInstanceMapUtil;
import com.mesalab.common.utils.RandomUtil;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.qgw.model.basic.*;
import com.mesalab.qgw.service.DSLService;
import com.mesalab.qgw.service.PacketCombineDslService;
import com.mesalab.qgw.service.QueryJobService;
import com.mesalab.qgw.service.SQLSyncQueryService;
import com.mesalab.services.common.enums.MetricFunction;
import com.mesalab.services.common.enums.MetricType;
import com.mesalab.services.common.property.SqlPropertySourceFactory;
import com.mesalab.services.configuration.JobConfig;
import com.mesalab.services.service.JobService;
import com.mesalab.services.service.impl.JobExecuteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * TODO
 *
 * @Classname QueryJobServiceImpl
 * @Date 2023/12/6 18:10
 * @Author wWei
 */
@Service
@PropertySource(value = "classpath:job-sql-template.sql", factory = SqlPropertySourceFactory.class)
public class QueryJobServiceImpl implements QueryJobService, EnvironmentAware {
    private static final Log log = LogFactory.get();
    private Environment env;
    private SQLSyncQueryService sqlSyncQueryService;
    private JobService jobService;
    private JobExecuteService jobExecuteService;
    private DSLService dslService;


    @Override
    public BaseResult createSQLSavedQuery(SqlQueryRequestParam request) {
        initAndValidationID(request);
        syntaxParseSql(request.getStatement());
        String sql = JSONUtil.quote(request.getStatement(), false).replace("'", "\\'");
        long currentTime = System.currentTimeMillis() / 1000;
        Db.update(String.format(Objects.requireNonNull(env.getProperty("SAVED_QUERY_JOB_INIT")), request.getId(), sql, currentTime, currentTime));
        log.info("Committed a SQL Saved Query Job, id is: {}", request.getId());
        return BaseResultGenerator.successCreate(buildJobInfoOfCreated(request.getId()));
    }

    @Override
    public BaseResult createSQLAdHocQuery(SqlQueryRequestParam sqlQueryRequestParam) {
        initAndValidationID(sqlQueryRequestParam);
        String option = sqlQueryRequestParam.isDryRun()
                ? QueryOption.SYNTAX_VALIDATION.getValue() : QueryOption.REAL_TIME.getValue();
        long start = System.currentTimeMillis();
        ExecutionMode execMode = sqlQueryRequestParam.getExecutionMode();
        if (ExecutionMode.ONESHOT.equals(execMode)) {
            return sqlSyncQueryService.executeQuery(SQLQueryContext.builder()
                    .originalSQL(sqlQueryRequestParam.getStatement())
                    .option(option)
                    .format(sqlQueryRequestParam.getOutputMode().getValue())
                    .build());
        } else if (ExecutionMode.NORMAL.equals(execMode)) {
            HazelcastInstanceMapUtil.put(sqlQueryRequestParam.getId(), new QueryCache(sqlQueryRequestParam.getId()));
            jobExecuteService.addExecutorSql(sqlQueryRequestParam);
            return BaseResultGenerator.successCreate(buildJobInfoOfCreated(sqlQueryRequestParam.getId()));
        } else if (ExecutionMode.BLOCKING.equals(execMode)) {
            BaseResult baseResult = sqlSyncQueryService.executeQuery(SQLQueryContext.builder().originalSQL(sqlQueryRequestParam.getStatement()).option(option).format(sqlQueryRequestParam.getOutputMode().getValue()).build());
            QueryCache queryCache = buildQueryCacheOfDone(sqlQueryRequestParam.getId(), start, baseResult);
            HazelcastInstanceMapUtil.put(sqlQueryRequestParam.getId(), queryCache);
            return BaseResultGenerator.successCreate(buildJobInfoOfCreated(sqlQueryRequestParam.getId()));
        }
        throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getCode(),
                String.format(CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getMessage(), "not Supported"));
    }

    @Override
    public BaseResult createDSLAdHocQuery(DSLQueryRequestParam request) {
        initAndValidationID(request);
        long start = System.currentTimeMillis();
        ExecutionMode execMode = request.getExecutionMode();
        if (ExecutionMode.ONESHOT.equals(execMode)) {
            if (JobConfig.FIELD_DISCOVERY.equals(request.getName())) {
                execAdnWaitFieldDiscoveryDone(request.getId(), request);
                QueryCache queryCache = rebuildFieldDiscoveryQueryCache(getAdhocQueryCacheAndRefresh(request.getId()));
                HazelcastInstanceMapUtil.remove(request.getId());
                BaseResult<Object> baseResult = queryCache.getBaseResult();
                Map<String, Object> job = baseResult.getJob();
                if (!BooleanUtil.toBoolean(String.valueOf(job.get(JobConfig.IS_CANCELED)))
                        && !BooleanUtil.toBoolean(String.valueOf(job.get(JobConfig.IS_FAILED)))
                        && BooleanUtil.toBoolean(String.valueOf(job.get(JobConfig.IS_DONE)))) {
                    return BaseResultGenerator.success(baseResult.getStatistics(), null, baseResult.getOutputMode(), baseResult.getMeta(), baseResult.getData());
                }
                return BaseResultGenerator.error(baseResult.getMessage());
            } else if (JobConfig.DATAPATH_PACKET_COMBINE.equals(request.getName())) {
                return jobExecuteService.addDslExecutorPacketCombineWithoutCache(request);
            } else if (JobConfig.TRAFFIC_SPECTRUM_SUMMARY.equals(request.getName())
                    || JobConfig.TRAFFIC_SPECTRUM_UNIQUE_CLIENT_AND_SERVER_IPS.equals(request.getName())
                    || JobConfig.TRAFFIC_SPECTRUM_APP_DISTRIBUTION.equals(request.getName())
                    || JobConfig.TRAFFIC_SPECTRUM_NETWORK_THROUGHPUT_TREND.equals(request.getName())
                    || JobConfig.TRAFFIC_SPECTRUM_CLIENT_IP_CONNECT_APPLICATION_USAGE.equals(request.getName())) {
                return jobExecuteService.addDslExecutorTrafficSpectrumWithoutCache(request);
            } else {
                DSLQueryContext dslQueryContext = BeanUtil.copyProperties(request, DSLQueryContext.class);
                return dslService.execDsl(dslQueryContext, request.isDryRun());
            }
        } else if (ExecutionMode.NORMAL.equals(execMode)) {
            QueryCache queryCache = new QueryCache(request.getId());
            if (JobConfig.FIELD_DISCOVERY.equals(request.getName())) {
                queryCache.setType(JobConfig.FIELD_DISCOVERY);
                validFieldDiscovery(request);
                HazelcastInstanceMapUtil.put(request.getId(), queryCache);
                jobExecuteService.addExecutorFieldDiscovery(request);
            } else if (JobConfig.DATAPATH_PACKET_COMBINE.equals(request.getName())) {
                HazelcastInstanceMapUtil.put(request.getId(), queryCache);
                jobExecuteService.addExecutorDslPacketCombineWithCache(request, null);
            } else if (JobConfig.TRAFFIC_SPECTRUM_SUMMARY.equals(request.getName())
                    || JobConfig.TRAFFIC_SPECTRUM_UNIQUE_CLIENT_AND_SERVER_IPS.equals(request.getName())
                    || JobConfig.TRAFFIC_SPECTRUM_APP_DISTRIBUTION.equals(request.getName())
                    || JobConfig.TRAFFIC_SPECTRUM_NETWORK_THROUGHPUT_TREND.equals(request.getName())
                    || JobConfig.TRAFFIC_SPECTRUM_CLIENT_IP_CONNECT_APPLICATION_USAGE.equals(request.getName())) {
                HazelcastInstanceMapUtil.put(request.getId(), queryCache);
                jobExecuteService.addDslExecutorTrafficSpectrumWithCache(request, null);
            } else {
                HazelcastInstanceMapUtil.put(request.getId(), queryCache);
                jobExecuteService.addExecutorDsl(request);
            }
            return BaseResultGenerator.successCreate(buildJobInfoOfCreated(request.getId()));
        } else if (ExecutionMode.BLOCKING.equals(execMode)) {
            if (JobConfig.FIELD_DISCOVERY.equals(request.getName())) {
                execAdnWaitFieldDiscoveryDone(request.getId(), request);
                return BaseResultGenerator.successCreate(buildJobInfoOfCreated(request.getId()));
            } else if (JobConfig.DATAPATH_PACKET_COMBINE.equals(request.getName())) {
                QueryCache queryCache = new QueryCache(request.getId());
                HazelcastInstanceMapUtil.put(request.getId(), queryCache);
                CountDownLatch countDownLatch = new CountDownLatch(1);
                jobExecuteService.addExecutorDslPacketCombineWithCache(request, countDownLatch);
                try {
                    countDownLatch.await();
                } catch (InterruptedException e) {
                    throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                            String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(), "Job was Interrupted. message: " + e.getMessage()));
                }
                return BaseResultGenerator.successCreate(buildJobInfoOfCreated(request.getId()));
            } else if (JobConfig.TRAFFIC_SPECTRUM_SUMMARY.equals(request.getName())
                    || JobConfig.TRAFFIC_SPECTRUM_UNIQUE_CLIENT_AND_SERVER_IPS.equals(request.getName())
                    || JobConfig.TRAFFIC_SPECTRUM_APP_DISTRIBUTION.equals(request.getName())
                    || JobConfig.TRAFFIC_SPECTRUM_NETWORK_THROUGHPUT_TREND.equals(request.getName())
                    || JobConfig.TRAFFIC_SPECTRUM_CLIENT_IP_CONNECT_APPLICATION_USAGE.equals(request.getName())) {
                QueryCache queryCache = new QueryCache(request.getId());
                HazelcastInstanceMapUtil.put(request.getId(), queryCache);
                CountDownLatch countDownLatch = new CountDownLatch(1);
                jobExecuteService.addDslExecutorTrafficSpectrumWithCache(request, countDownLatch);
                try {
                    countDownLatch.await();
                } catch (InterruptedException e) {
                    throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                            String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(), "Job was Interrupted. message: " + e.getMessage()));
                }
                return BaseResultGenerator.successCreate(buildJobInfoOfCreated(request.getId()));
            } else {
                DSLQueryContext dslQueryContext = BeanUtil.copyProperties(request, DSLQueryContext.class);
                BaseResult baseResult = dslService.execDsl(dslQueryContext, request.isDryRun());
                QueryCache queryCache = buildQueryCacheOfDone(request.getId(), start, baseResult);
                HazelcastInstanceMapUtil.put(request.getId(), queryCache);
                return BaseResultGenerator.successCreate(buildJobInfoOfCreated(request.getId()));
            }
        }
        throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getCode(),
                String.format(CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getMessage(), "not Supported"));
    }

    private void execAdnWaitFieldDiscoveryDone(String id, DSLQueryRequestParam request) {
        QueryCache queryCacheStart = new QueryCache(id);
        queryCacheStart.setType(JobConfig.FIELD_DISCOVERY);
        validFieldDiscovery(request);
        HazelcastInstanceMapUtil.put(id, queryCacheStart);
        Future<Boolean> booleanFuture = jobExecuteService.addExecutorFieldDiscovery(request);
        while (true) {
            if (!booleanFuture.isDone()) {
                continue;
            }
            break;
        }
    }

    private QueryCache buildQueryCacheOfDone(String id, long start, BaseResult baseResult) {
        QueryCache queryCache = new QueryCache(id);
        Map<String, Object> jobInfo = queryCache.getBaseResult().getJob();
        jobInfo.put(JobConfig.IS_DONE, true);
        jobInfo.put(JobConfig.DONE_PROGRESS, 1);
        if (!baseResult.isSuccess()) {
            jobInfo.put(JobConfig.IS_FAILED, false);
            jobInfo.put(JobConfig.REASON, baseResult.getMessage());
        }
        jobInfo.put(JobConfig.START_TIME, DateUtils.convertTimestampToString(start / 1000, DatePattern.UTC_PATTERN));
        jobInfo.put(JobConfig.END_TIME, DateUtils.convertTimestampToString(System.currentTimeMillis() / 1000, DatePattern.UTC_PATTERN));
        baseResult.setJob(jobInfo);
        queryCache.setBaseResult(baseResult);
        return queryCache;
    }

    @Override
    public BaseResult getAdHocQueryResultById(String id) {
        QueryCache queryCache = getAdhocQueryCacheAndRefresh(id);
        if(queryCache == null){
            return BaseResultGenerator.success(Lists.newArrayList());
        }
        if (JobConfig.FIELD_DISCOVERY.equals(queryCache.getType())) {
            queryCache = rebuildFieldDiscoveryQueryCache(queryCache);
        }
        BaseResult<Object> baseResult = queryCache.getBaseResult();
        return BaseResultGenerator.success(baseResult.getStatistics(), baseResult.getJob(), baseResult.getOutputMode(), baseResult.getMeta(), baseResult.getData());
    }

    @Override
    public BaseResult getSavedQueryResultById(String id) {
        return jobService.getSavedQueryResult(id);
    }

    @Override
    public BaseResult getAdHocQueryStatusById(String id) {
        QueryCache queryCache = getAdhocQueryCacheAndRefresh(id);
        if(queryCache == null){
            return BaseResultGenerator.success(Lists.newArrayList());
        }
        return BaseResultGenerator.success(null, queryCache.getBaseResult().getJob(), null, null, null);
    }

    @Override
    public BaseResult getSavedQueryStatusById(String id) {
        return jobService.getSavedQueryStatus(id);
    }

    @Override
    public BaseResult getSavedQueryResult(List<String> ids) {
        List<Map<String, Object>> result = jobService.batchSavedQueryResult(ids);
        return BaseResultGenerator.success(result);
    }

    @Override
    public BaseResult getAdHocQueryResult(List<String> ids) {
        List<Object> results = Lists.newArrayList();
        for (String id : ids) {
            BaseResult<Object> baseResult;
            QueryCache queryCache = getAdhocQueryCacheAndRefresh(id);
            if(queryCache == null){
                continue;
            }
            if (JobConfig.FIELD_DISCOVERY.equals(queryCache.getType())) {
                queryCache = rebuildFieldDiscoveryQueryCache(queryCache);
            }
            baseResult = queryCache.getBaseResult();
            Map<String, Object> resultItem = Maps.newLinkedHashMap();
            resultItem.put(JobConfig.STATISTICS, baseResult.getStatistics());
            resultItem.put(JobConfig.JOB, baseResult.getJob());
            resultItem.put(JobConfig.META, baseResult.getMeta());
            resultItem.put(JobConfig.DATA, baseResult.getData());
            results.add(resultItem);
        }
        return BaseResultGenerator.success(results);
    }

    @Override
    public BaseResult getSavedQueryStatus(List<String> ids) {
        List<Map<String, Object>> result = jobService.batchSavedQueryStatus(ids);
        return BaseResultGenerator.success(result);
    }

    @Override
    public BaseResult getAdHocQueryStatus(List<String> ids) {
        List<Object> results = Lists.newArrayList();
        for (String id : ids) {
            QueryCache queryCache = getAdhocQueryCacheAndRefresh(id);
            if(queryCache == null){
                continue;
            }
            results.add(queryCache.getBaseResult().getJob());
        }
        return BaseResultGenerator.success(results);
    }

    @Override
    public BaseResult deleteSavedQueryById(String id) {
        return jobService.cancelSavedQuery(id);
    }

    private void validFieldDiscovery(DSLQueryRequestParam request) {
        Object filter = request.getFilter();
        if (StrUtil.isEmptyIfStr(filter)) {
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.QUERY_CUSTOM_FIELD_DISCOVERY_ERROR));
        }

        Object metric = request.getCustomRequestParam().get(JobConfig.KEY_CUSTOM_FIELD_DISCOVERY_METRIC);
        if (StringUtil.isEmpty(metric)) {
            return;
        }
        boolean validMetric = MetricType.isValid(String.valueOf(metric));
        boolean validFn = MetricFunction.isValid(String.valueOf(request.getCustomRequestParam().get(JobConfig.KEY_CUSTOM_FIELD_DISCOVERY_METRIC_FN)));
        if (!validMetric || !validFn) {
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.QUERY_CUSTOM_FIELD_DISCOVERY_ERROR));
        }
        Object fields = request.getCustomRequestParam().get(JobConfig.KEY_CUSTOM_FIELD_DISCOVERY_FIELDS);
        if (StrUtil.isEmptyIfStr(fields)) {
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.QUERY_CUSTOM_FIELD_DISCOVERY_ERROR));
        }
        List<String> fieldList = (List<String>) request.getCustomRequestParam().get(JobConfig.KEY_CUSTOM_FIELD_DISCOVERY_FIELDS);
        request.getCustomRequestParam().put(JobConfig.KEY_CUSTOM_FIELD_DISCOVERY_FIELDS, fieldList.stream().distinct().collect(Collectors.toList()));
    }

    private void syntaxParseSql(String sql) {
        BaseResult baseResult = sqlSyncQueryService.executeQuery(SQLQueryContext.builder().originalSQL(sql).option(QueryOption.SYNTAX_PARSE.getValue()).build());
        if (!baseResult.isSuccess()) {
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), baseResult.getMessage()));
        }
    }

    private static String generateJobId() {
        return RandomUtil.getUUID();
    }

    private static Map<String, Object> buildJobInfoOfCreated(String id) {
        Map<String, Object> result = Maps.newHashMap();
        result.put(JobConfig.JOB_ID, id);
        return result;
    }

    private static QueryCache getAdhocQueryCacheAndRefresh(String id) {
        try {
            HazelcastInstanceMapUtil.retrieveMap().lock(id);
            QueryCache queryCache = HazelcastInstanceMapUtil.get(id);
            if (queryCache == null) {
                return null;
            }
            queryCache.setLatestQueryTimeMs(System.currentTimeMillis());
            HazelcastInstanceMapUtil.put(id, queryCache);
            return queryCache;
        } finally {
            HazelcastInstanceMapUtil.retrieveMap().unlock(id);
        }
    }

    private static QueryCache rebuildFieldDiscoveryQueryCache(QueryCache queryCache) {
        BaseResult<Object> baseResult = queryCache.getBaseResult();
        Object dataObj = baseResult.getData();
        if (!StrUtil.isEmptyIfStr(dataObj)) {
            List<Map> dataList = (List<Map>) dataObj;
            for (Map map : dataList) {
                for (Object itemObj : map.keySet()) {
                    Map<String, Object> itemMap = (Map) map.get(itemObj);
                    Object topkObj = itemMap.get("topk");
                    if (!StrUtil.isEmptyIfStr(topkObj)) {
                        List<Map<String, Object>> topkList = (List<Map<String, Object>>) topkObj;
                        List<Map<String, Object>> list = topkList.subList(0, Math.min(10, topkList.size()));
                        itemMap.put("topk", Lists.newArrayList(list));
                    }
                }
            }
            return queryCache;
        }
        return queryCache;
    }

    private static void initAndValidationID(CommonRequestParam request) {
        String id = StrUtil.isBlank(request.getId()) ? generateJobId() : request.getId();
        QueryCache queryCache = HazelcastInstanceMapUtil.get(id);
        if (queryCache != null) {
            Map<String, Object> job = queryCache.getBaseResult().getJob();
            boolean isFailed = BooleanUtil.toBoolean(String.valueOf(job.get(JobConfig.IS_FAILED)));
            if (!isFailed) {
                throw new QGWBusinessException(HttpStatusCodeEnum.REQ_FORBIDDEN.getCode(), CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getCode(),
                        String.format(CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getMessage(), "Job id already exists"));
            }
        }
        request.setId(id);
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.env = environment;
    }

    @Autowired
    public void setJobService(JobService jobService) {
        this.jobService = jobService;
    }

    @Autowired
    public void setJobExecuteService(JobExecuteService jobExecuteService) {
        this.jobExecuteService = jobExecuteService;
    }

    @Autowired
    public void setSqlSyncQueryService(SQLSyncQueryService sqlSyncQueryService) {
        this.sqlSyncQueryService = sqlSyncQueryService;
    }

    @Autowired
    public void setDslService(DSLService dslService) {
        this.dslService = dslService;
    }
}