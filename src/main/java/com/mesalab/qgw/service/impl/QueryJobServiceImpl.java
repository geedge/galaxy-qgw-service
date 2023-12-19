package com.mesalab.qgw.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.geedgenetworks.utils.StringUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.*;
import com.mesalab.common.utils.HazelcastInstanceMapUtil;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.qgw.model.basic.*;
import com.mesalab.qgw.service.DSLService;
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
import org.springframework.stereotype.Service;

import java.util.*;
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
        syntaxParseSql(request.getStatement());
        String sql = JSONUtil.quote(request.getStatement(), false).replace("'", "\\'");
        String id = getSameJob(sql);
        if (StringUtil.isNotBlank(id)) {
            return BaseResultGenerator.successCreate(buildJobInfoOfCreated(id));
        }
        request.getCustomRequestParam().put("UUID", System.currentTimeMillis());
        id = buildJobId(request.toString());
        long currentTime = System.currentTimeMillis() / 1000;
        Db.update(String.format(Objects.requireNonNull(env.getProperty("SAVED_QUERY_JOB_INIT")), id, sql, currentTime, currentTime));
        log.info("Committed a SQL Saved Query Job, id is: {}", id);
        return BaseResultGenerator.successCreate(buildJobInfoOfCreated(id));
    }

    @Override
    public BaseResult createSQLAdHocQuery(SqlQueryRequestParam sqlQueryRequestParam) {
        String option = sqlQueryRequestParam.isDryRun() ? QueryOption.SYNTAX_VALIDATION.getValue() : QueryOption.REAL_TIME.getValue();
        sqlQueryRequestParam.getCustomRequestParam().put("UUID", System.currentTimeMillis());
        long start = System.currentTimeMillis();
        ExecMode execMode = sqlQueryRequestParam.getExecMode();
        if (ExecMode.ONESHOT.equals(execMode)) {
            return sqlSyncQueryService.executeQuery(QueryContext.builder().query(sqlQueryRequestParam.getStatement()).option(option).format(sqlQueryRequestParam.getOutputMode().getValue()).build());
        } else if (ExecMode.NORMAL.equals(execMode)) {
            String id = buildJobId(sqlQueryRequestParam.toString());
            HazelcastInstanceMapUtil.put(id, new QueryCache(id, sqlQueryRequestParam.getOutputMode().getValue()));
            jobExecuteService.addExecutorSql(id, sqlQueryRequestParam);
            return BaseResultGenerator.successCreate(buildJobInfoOfCreated(id));
        } else if (ExecMode.BLOCKING.equals(execMode)) {
            String id = buildJobId(sqlQueryRequestParam.toString());
            BaseResult baseResult = sqlSyncQueryService.executeQuery(QueryContext.builder().query(sqlQueryRequestParam.getStatement()).option(option).format(sqlQueryRequestParam.getOutputMode().getValue()).build());
            QueryCache queryCache = buildQueryCacheOfDone(id, start, sqlQueryRequestParam.getOutputMode().getValue(), baseResult);
            HazelcastInstanceMapUtil.put(id, queryCache);
            return BaseResultGenerator.successCreate(buildJobInfoOfCreated(id));
        }
        throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getCode(),
                String.format(CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getMessage(), "not Supported"));
    }

    @Override
    public BaseResult createDSLAdHocQuery(DSLQueryRequestParam request) {
        request.getCustomRequestParam().put("UUID", System.currentTimeMillis());
        String id = buildJobId(request.toString());
        long start = System.currentTimeMillis();
        ExecMode execMode = request.getExecMode();
        if (ExecMode.ONESHOT.equals(execMode)) {
            if (JobConfig.FIELD_DISCOVERY.equals(request.getName())) {
                throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getCode(),
                        String.format(CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getMessage(), JobConfig.FIELD_DISCOVERY + " only Supported exec_mode is" + ExecMode.NORMAL.getValue()));
            } else {
                DSLProfile dslProfile = BeanUtil.copyProperties(request, DSLProfile.class);
                return dslService.execDsl(dslProfile);
            }
        } else if (ExecMode.NORMAL.equals(execMode)) {
            QueryCache queryCache = new QueryCache(id, request.getOutputMode().getValue());
            if (JobConfig.FIELD_DISCOVERY.equals(request.getName())) {
                queryCache.setType(JobConfig.FIELD_DISCOVERY);
                validFieldDiscovery(request);
                HazelcastInstanceMapUtil.put(id, queryCache);
                jobExecuteService.addExecutorFieldDiscovery(id, request);
            } else {
                HazelcastInstanceMapUtil.put(id, queryCache);
                jobExecuteService.addExecutorDsl(id, request);
            }
            return BaseResultGenerator.successCreate(buildJobInfoOfCreated(id));
        } else if (ExecMode.BLOCKING.equals(execMode)) {
            if (JobConfig.FIELD_DISCOVERY.equals(request.getName())) {
                throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getCode(),
                        String.format(CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getMessage(), JobConfig.FIELD_DISCOVERY + " only Supported exec_mode is" + ExecMode.NORMAL.getValue()));
            } else {
                DSLProfile dslProfile = BeanUtil.copyProperties(request, DSLProfile.class);
                BaseResult baseResult = dslService.execDsl(dslProfile);
                QueryCache queryCache = buildQueryCacheOfDone(id, start, request.getOutputMode().getValue(), baseResult);
                HazelcastInstanceMapUtil.put(id, queryCache);
                return BaseResultGenerator.successCreate(buildJobInfoOfCreated(id));
            }
        }
        throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getCode(),
                String.format(CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getMessage(), "not Supported"));
    }

    private QueryCache buildQueryCacheOfDone(String id, long start, String outputMode, BaseResult baseResult) {
        QueryCache queryCache = new QueryCache(id, outputMode);
        Map<String, Object> jobInfo = queryCache.getBaseResult().getJob();
        jobInfo.put(JobConfig.IS_DONE, true);
        jobInfo.put(JobConfig.DONE_PROGRESS, 1);
        if (!baseResult.isSuccess()) {
            jobInfo.put(JobConfig.IS_FAILED, false);
        }
        jobInfo.put(JobConfig.START_TIME, start / 1000);
        jobInfo.put(JobConfig.END_TIME, System.currentTimeMillis() / 1000);
        baseResult.setJob(jobInfo);
        queryCache.setBaseResult(baseResult);
        return queryCache;
    }

    @Override
    public BaseResult getAdHocQueryResultById(String id) {
        QueryCache queryCache = getAdhocQueryCache(id);
        if (JobConfig.FIELD_DISCOVERY.equals(queryCache.getType())) {
            queryCache = rebuildFieldDiscoveryQueryCache(queryCache);
        }
        BaseResult<Object> baseResult = queryCache.getBaseResult();
        return BaseResultGenerator.success(baseResult.getStatistics(), baseResult.getJob(), baseResult.getMeta(), baseResult.getData());
    }

    @Override
    public BaseResult getSavedQueryResultById(String id) {
        return jobService.getSavedQueryResult(id);
    }

    @Override
    public BaseResult getAdHocQueryStatusById(String id) {
        QueryCache queryCache = getAdhocQueryCache(id);
        return BaseResultGenerator.success(null, queryCache.getBaseResult().getJob(), null, null);
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
            QueryCache queryCache = getAdhocQueryCache(id);
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
            QueryCache queryCache = getAdhocQueryCache(id);
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
        BaseResult baseResult = sqlSyncQueryService.executeQuery(QueryContext.builder().query(sql).option(QueryOption.SYNTAX_PARSE.getValue()).build());
        if (!baseResult.isSuccess()) {
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), baseResult.getMessage()));
        }
    }

    private String getSameJob(String sql) {
        List<Record> jobs = Db.find(String.format(Objects.requireNonNull(env.getProperty("SAVED_QUERY_JOB_SAME")), sql));
        if (jobs.isEmpty()) {
            return StringUtil.EMPTY;
        }
        return jobs.get(0).get("id");
    }

    private static String buildJobId(String request) {
        return DigestUtil.md5Hex(request);
    }

    private static Map<String, Object> buildJobInfoOfCreated(String id) {
        Map<String, Object> result = Maps.newHashMap();
        result.put(JobConfig.JOB_ID, id);
        return result;
    }

    private static QueryCache getAdhocQueryCache(String id) {
        QueryCache queryCache = HazelcastInstanceMapUtil.get(id);
        if (queryCache == null) {
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getMessage(), id + ", Does not exist or result expired."));
        }
        queryCache.setLatestQueryTimeMs(System.currentTimeMillis());
        HazelcastInstanceMapUtil.put(id, queryCache);
        return queryCache;
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