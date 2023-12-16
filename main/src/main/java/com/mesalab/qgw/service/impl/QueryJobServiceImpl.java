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
import com.mesalab.qgw.exception.QGWErrorCode;
import com.mesalab.qgw.model.basic.*;
import com.mesalab.qgw.service.DslService;
import com.mesalab.qgw.service.QueryJobService;
import com.mesalab.qgw.service.QueryService;
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
    private QueryService queryService;
    private JobService jobService;
    private JobExecuteService jobExecuteService;
    private DslService dslService;

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
        String option = sqlQueryRequestParam.isDryRun() ? QueryOptionEnum.SYNTAX_VALIDATION.getValue() : QueryOptionEnum.REAL_TIME.getValue();
        sqlQueryRequestParam.getCustomRequestParam().put("UUID", System.currentTimeMillis());
        long start = System.currentTimeMillis();
        ExecModeEnum execMode = sqlQueryRequestParam.getExecMode();
        if (ExecModeEnum.ONESHOT.equals(execMode)) {
            return queryService.executeQuery(QueryProfile.builder().query(sqlQueryRequestParam.getStatement()).option(option).format(sqlQueryRequestParam.getOutputMode().getValue()).build());
        } else if (ExecModeEnum.NORMAL.equals(execMode)) {
            String id = buildJobId(sqlQueryRequestParam.toString());
            HazelcastInstanceMapUtil.put(id, new QueryCache(id, sqlQueryRequestParam.getOutputMode().getValue()));
            jobExecuteService.addExecutorSql(id, sqlQueryRequestParam);
            return BaseResultGenerator.successCreate(buildJobInfoOfCreated(id));
        } else if (ExecModeEnum.BLOCKING.equals(execMode)) {
            String id = buildJobId(sqlQueryRequestParam.toString());
            BaseResult baseResult = queryService.executeQuery(QueryProfile.builder().query(sqlQueryRequestParam.getStatement()).option(option).format(sqlQueryRequestParam.getOutputMode().getValue()).build());
            QueryCache queryCache = buildQueryCacheOfDone(id, start, sqlQueryRequestParam.getOutputMode().getValue(), baseResult);
            HazelcastInstanceMapUtil.put(id, queryCache);
            return BaseResultGenerator.successCreate(buildJobInfoOfCreated(id));
        }
        throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), QGWErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getCode(),
                String.format(QGWErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getMessage(), "not Supported"));
    }

    @Override
    public BaseResult createDSLAdHocQuery(DslQueryRequestParam request) {
        request.getCustomRequestParam().put("UUID", System.currentTimeMillis());
        String id = buildJobId(request.toString());
        long start = System.currentTimeMillis();
        ExecModeEnum execMode = request.getExecMode();
        if (ExecModeEnum.ONESHOT.equals(execMode)) {
            if (JobConfig.FIELD_DISCOVERY.equals(request.getName())) {
                throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), QGWErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getCode(),
                        String.format(QGWErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getMessage(), JobConfig.FIELD_DISCOVERY + " only Supported exec_mode is" + ExecModeEnum.NORMAL.getValue()));
            } else {
                DSLProfile dslProfile = BeanUtil.copyProperties(request, DSLProfile.class);
                return dslService.execDsl(dslProfile);
            }
        } else if (ExecModeEnum.NORMAL.equals(execMode)) {
            QueryCache queryCache = new QueryCache(id, request.getOutputMode().getValue());
            queryCache.setType(JobConfig.FIELD_DISCOVERY);
            HazelcastInstanceMapUtil.put(id, queryCache);
            if (JobConfig.FIELD_DISCOVERY.equals(request.getName())) {
                validFieldDiscovery(request);
                jobExecuteService.addExecutorFieldDiscovery(id, request);
            } else {
                jobExecuteService.addExecutorDsl(id, request);
            }
            return BaseResultGenerator.successCreate(buildJobInfoOfCreated(id));
        } else if (ExecModeEnum.BLOCKING.equals(execMode)) {
            if (JobConfig.FIELD_DISCOVERY.equals(request.getName())) {
                throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), QGWErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getCode(),
                        String.format(QGWErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getMessage(), JobConfig.FIELD_DISCOVERY + " only Supported exec_mode is" + ExecModeEnum.NORMAL.getValue()));
            } else {
                DSLProfile dslProfile = BeanUtil.copyProperties(request, DSLProfile.class);
                BaseResult baseResult = dslService.execDsl(dslProfile);
                QueryCache queryCache = buildQueryCacheOfDone(id, start, request.getOutputMode().getValue(), baseResult);
                HazelcastInstanceMapUtil.put(id, queryCache);
                return BaseResultGenerator.successCreate(buildJobInfoOfCreated(id));
            }
        }
        throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), QGWErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getCode(),
                String.format(QGWErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getMessage(), "not Supported"));
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
        QueryCache queryCache = HazelcastInstanceMapUtil.get(id);
        updateLastQueryTime(queryCache, id);
        if (JobConfig.FIELD_DISCOVERY.equals(queryCache.getType())) {
            queryCache = buildFieldDiscoveryBaseResultData(id);
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
        QueryCache queryCache = HazelcastInstanceMapUtil.get(id);
        updateLastQueryTime(queryCache, id);
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
            QueryCache queryCache = HazelcastInstanceMapUtil.get(id);
            updateLastQueryTime(queryCache, id);
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
            QueryCache queryCache = HazelcastInstanceMapUtil.get(id);
            updateLastQueryTime(queryCache, id);
            results.add(queryCache.getBaseResult().getJob());
        }
        return BaseResultGenerator.success(results);
    }

    @Override
    public BaseResult deleteSavedQueryById(String id) {
        return jobService.cancelSavedQuery(id);
    }

    private void validFieldDiscovery(DslQueryRequestParam request) {
        Object filter = request.getFilter();
        if (StrUtil.isEmptyIfStr(filter)) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), QGWMessageConst.QUERY_CUSTOM_FIELD_DISCOVERY_ERROR));
        }

        Object metric = request.getCustomRequestParam().get(JobConfig.KEY_CUSTOM_FIELD_DISCOVERY_METRIC);
        if (StringUtil.isEmpty(metric)) {
            return;
        }
        boolean validMetric = MetricType.isValid(String.valueOf(metric));
        boolean validFn = MetricFunction.isValid(String.valueOf(request.getCustomRequestParam().get(JobConfig.KEY_CUSTOM_FIELD_DISCOVERY_METRIC_FN)));
        if (!validMetric || !validFn) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), QGWMessageConst.QUERY_CUSTOM_FIELD_DISCOVERY_ERROR));
        }
        Object fields = request.getCustomRequestParam().get(JobConfig.KEY_CUSTOM_FIELD_DISCOVERY_FIELDS);
        if (StrUtil.isEmptyIfStr(fields)) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), QGWMessageConst.QUERY_CUSTOM_FIELD_DISCOVERY_ERROR));
        }
        List<String> fieldList = (List<String>) request.getCustomRequestParam().get(JobConfig.KEY_CUSTOM_FIELD_DISCOVERY_FIELDS);
        request.getCustomRequestParam().put(JobConfig.KEY_CUSTOM_FIELD_DISCOVERY_FIELDS, fieldList.stream().distinct().collect(Collectors.toList()));
    }

    private void syntaxParseSql(String sql) {
        BaseResult baseResult = queryService.executeQuery(QueryProfile.builder().query(sql).option(QueryOptionEnum.SYNTAX_PARSE.getValue()).build());
        if (!baseResult.isSuccess()) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), baseResult.getMessage()));
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

    private static void updateLastQueryTime(QueryCache queryCache, String id) {
        if (queryCache == null) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), QGWErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getCode(),
                    String.format(QGWErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getMessage(), id + ", Does not exist or result expired."));
        }
        queryCache.setLatestQueryTimeMs(System.currentTimeMillis());
        HazelcastInstanceMapUtil.put(id, queryCache);
    }

    private static QueryCache buildFieldDiscoveryBaseResultData(String id) {
        QueryCache queryCache = HazelcastInstanceMapUtil.get(id);
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
    public void setQueryService(QueryService queryService) {
        this.queryService = queryService;
    }

    @Autowired
    public void setDslService(DslService dslService) {
        this.dslService = dslService;
    }
}