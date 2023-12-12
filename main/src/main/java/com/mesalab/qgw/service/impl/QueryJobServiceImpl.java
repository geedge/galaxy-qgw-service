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
import com.mesalab.common.utils.HazelcastInstanceUtil;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.qgw.exception.QGWErrorCode;
import com.mesalab.qgw.model.basic.*;
import com.mesalab.qgw.service.DslService;
import com.mesalab.qgw.service.QueryJobService;
import com.mesalab.qgw.service.QueryService;
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
    public BaseResult commitSqlSavedQuery(SqlQueryRequest request) {
        syntaxParseSql(request.getStatement());
        String sql = JSONUtil.quote(request.getStatement(), false).replace("'", "\\'");
        String id = getSameJob(sql);
        if (StringUtil.isNotBlank(id)) {
            return BaseResultGenerator.successCreate(buildJobInfoOfCreated(id));
        }
        request.getCustomAttributes().put("UUID", System.currentTimeMillis());
        id = buildJobId(request.toString());
        long currentTime = System.currentTimeMillis() / 1000;
        Db.update(String.format(Objects.requireNonNull(env.getProperty("SAVED_QUERY_JOB_INIT")), id, sql, currentTime, currentTime));
        log.info("Committed a SQL Saved Query Job, id is: {}", id);
        return BaseResultGenerator.successCreate(buildJobInfoOfCreated(id));
    }

    @Override
    public BaseResult commitSqlAdHoc(SqlQueryRequest request) {
        String option = request.isDryRun() ? QueryOptionEnum.SYNTAX_VALIDATION.getValue() : QueryOptionEnum.REAL_TIME.getValue();
        request.getCustomAttributes().put("UUID", System.currentTimeMillis());
        long start = System.currentTimeMillis();
        ExecModeEnum execMode = request.getExecMode();
        if (ExecModeEnum.ONESHOT.equals(execMode)) {
            return queryService.executeQuery(QueryProfile.builder().query(request.getStatement()).option(option).format(request.getOutputMode().getValue()).build());
        } else if (ExecModeEnum.NORMAL.equals(execMode)) {
            String id = buildJobId(request.toString());
            HazelcastInstanceUtil.put(id, new QueryCache(id, request.getOutputMode().getValue()));
            jobExecuteService.addExecutorSql(id, request);
            return BaseResultGenerator.successCreate(buildJobInfoOfCreated(id));
        } else if (ExecModeEnum.BLOCKING.equals(execMode)) {
            String id = buildJobId(request.toString());
            BaseResult baseResult = queryService.executeQuery(QueryProfile.builder().query(request.getStatement()).option(option).format(request.getOutputMode().getValue()).build());
            QueryCache queryCache = buildQueryCacheOfDone(id, start, request.getOutputMode().getValue(), baseResult);
            HazelcastInstanceUtil.put(id, queryCache);
            return BaseResultGenerator.successCreate(buildJobInfoOfCreated(id));
        }
        throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), QGWErrorCode.PARAM_SYNTAX_BAD_REQUEST_EXCEPTION.getCode(),
                String.format(QGWErrorCode.PARAM_SYNTAX_BAD_REQUEST_EXCEPTION.getMessage(), "not Supported"));
    }

    @Override
    public BaseResult commitDslAdHoc(DslQueryRequest request) {
        request.getCustomAttributes().put("UUID", System.currentTimeMillis());
        long start = System.currentTimeMillis();
        ExecModeEnum execMode = request.getExecMode();
        if (ExecModeEnum.ONESHOT.equals(execMode)) {
            DSLProfile dslProfile = BeanUtil.copyProperties(request, DSLProfile.class);
            return dslService.execDsl(dslProfile);
        } else if (ExecModeEnum.NORMAL.equals(execMode)) {
            if (JobConfig.FIELD_DISCOVERY.equals(request.getName())) {
                HashMap<String, Object> map = Maps.newHashMap();
                map.put(JobConfig.KEY_QUERY_TYPE, JobConfig.FIELD_DISCOVERY);
                map.put(JobConfig.QUERY_DATA_SOURCE, request.getDataSource());
                map.put(JobConfig.FILTER, request.getFilter());
                map.put(JobConfig.KEY_CUSTOM_FIELD_DISCOVERY_FIELDS, request.getCustomAttributes().get(JobConfig.KEY_CUSTOM_FIELD_DISCOVERY_FIELDS));
                map.put(JobConfig.KEY_CUSTOM_FIELD_DISCOVERY_METRIC, request.getCustomAttributes().get(JobConfig.KEY_CUSTOM_FIELD_DISCOVERY_METRIC));
                map.put(JobConfig.KEY_CUSTOM_FIELD_DISCOVERY_METRIC_FN, request.getCustomAttributes().get(JobConfig.KEY_CUSTOM_FIELD_DISCOVERY_METRIC_FN));
                return jobService.commitAdHocQuery(map);
            } else {
                String id = buildJobId(request.toString());
                HazelcastInstanceUtil.put(id, new QueryCache(id, request.getOutputMode().getValue()));
                jobExecuteService.addExecutorDsl(id, request);
                return BaseResultGenerator.successCreate(buildJobInfoOfCreated(id));
            }
        } else if (ExecModeEnum.BLOCKING.equals(execMode)) {
            if (JobConfig.FIELD_DISCOVERY.equals(request.getName())) {
                throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), QGWErrorCode.PARAM_SYNTAX_BAD_REQUEST_EXCEPTION.getCode(),
                        String.format(QGWErrorCode.PARAM_SYNTAX_BAD_REQUEST_EXCEPTION.getMessage(), JobConfig.FIELD_DISCOVERY + " only Supported exec_mode is" + ExecModeEnum.NORMAL.getValue()));
            } else {
                DSLProfile dslProfile = BeanUtil.copyProperties(request, DSLProfile.class);
                BaseResult baseResult = dslService.execDsl(dslProfile);
                String id = buildJobId(request.toString());
                QueryCache queryCache = buildQueryCacheOfDone(id, start, request.getOutputMode().getValue(), baseResult);
                HazelcastInstanceUtil.put(id, queryCache);
                return BaseResultGenerator.successCreate(buildJobInfoOfCreated(id));
            }
        }
        throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), QGWErrorCode.PARAM_SYNTAX_BAD_REQUEST_EXCEPTION.getCode(),
                String.format(QGWErrorCode.PARAM_SYNTAX_BAD_REQUEST_EXCEPTION.getMessage(), "not Supported"));
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
    public BaseResult getAdHocResult(String id) {
        QueryCache queryCache = HazelcastInstanceUtil.get(id);
        if (StrUtil.isEmptyIfStr(queryCache)) {
            return jobService.getResultFieldDiscovery(id);
        } else {
            BaseResult<Object> baseResult = queryCache.getBaseResult();
            return BaseResultGenerator.success(baseResult.getStatistics(), baseResult.getJob(), baseResult.getMeta(), baseResult.getData());
        }
    }

    @Override
    public BaseResult getSavedQueryResult(String id) {
        return jobService.getSavedQueryResult(id);
    }

    @Override
    public BaseResult getAdHocStatus(String id) {
        QueryCache queryCache = HazelcastInstanceUtil.get(id);
        if (StrUtil.isEmptyIfStr(queryCache)) {
            BaseResult resultFieldDiscovery = jobService.getResultFieldDiscovery(id);
            if (StrUtil.isEmptyIfStr(resultFieldDiscovery)) {
                throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), QGWErrorCode.PARAM_SYNTAX_BAD_REQUEST_EXCEPTION.getCode(),
                        String.format(QGWErrorCode.PARAM_SYNTAX_BAD_REQUEST_EXCEPTION.getMessage(), "Does not exist or result expired."));
            }
            return BaseResultGenerator.success(null, resultFieldDiscovery.getJob(), null, null);
        } else {
            return BaseResultGenerator.success(null, queryCache.getBaseResult().getJob(), null, null);
        }
    }

    @Override
    public BaseResult getSavedQueryStatus(String id) {
        return jobService.getSavedQueryStatus(id);
    }

    @Override
    public BaseResult getSavedQueryResultBatch(List<String> ids) {
        List<Map<String, Object>> result = jobService.batchSavedQueryResult(ids);
        return BaseResultGenerator.success(result);
    }

    @Override
    public BaseResult getAdHocResultBatch(List<String> ids) {
        List<Object> results = Lists.newArrayList();
        for (String id : ids) {
            BaseResult<Object> baseResult;
            QueryCache queryCache = HazelcastInstanceUtil.get(id);
            if (StrUtil.isEmptyIfStr(queryCache)) {
                baseResult = jobService.getResultFieldDiscovery(id);
                if (StrUtil.isEmptyIfStr(baseResult)) {
                    throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), QGWErrorCode.PARAM_SYNTAX_BAD_REQUEST_EXCEPTION.getCode(),
                            String.format(QGWErrorCode.PARAM_SYNTAX_BAD_REQUEST_EXCEPTION.getMessage(), "Does not exist or result expired."));
                }
            } else {
                baseResult = queryCache.getBaseResult();
            }
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
    public BaseResult getSavedQueryStatusBatch(List<String> ids) {
        List<Map<String, Object>> result = jobService.batchSavedQueryStatus(ids);
        return BaseResultGenerator.success(result);
    }

    @Override
    public BaseResult getAdHocStatusBatch(List<String> ids) {
        List<Object> results = Lists.newArrayList();
        for (String id : ids) {
            QueryCache queryCache = HazelcastInstanceUtil.get(id);
            if (StrUtil.isEmptyIfStr(queryCache)) {
                BaseResult resultFieldDiscovery = jobService.getResultFieldDiscovery(id);
                if (StrUtil.isEmptyIfStr(resultFieldDiscovery)) {
                    throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), QGWErrorCode.PARAM_SYNTAX_BAD_REQUEST_EXCEPTION.getCode(),
                            String.format(QGWErrorCode.PARAM_SYNTAX_BAD_REQUEST_EXCEPTION.getMessage(), "Does not exist or result expired."));
                }
                results.add(resultFieldDiscovery.getJob());
            } else {
                results.add(queryCache.getBaseResult().getJob());
            }
        }
        return BaseResultGenerator.success(results);
    }

    @Override
    public BaseResult cancelSavedQuery(String id) {
        return jobService.cancelSavedQuery(id);
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
