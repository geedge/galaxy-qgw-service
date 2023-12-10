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
import com.mesalab.common.enums.ExecModeEnum;
import com.mesalab.common.enums.QueryOptionEnum;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.utils.HazelcastInstanceUtil;
import com.mesalab.qgw.controller.DslController;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.qgw.exception.QGWErrorCode;
import com.mesalab.qgw.model.basic.*;
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
    private DslController dslController;

    @Override
    public BaseResult commitSqlSavedQuery(SqlQueryRequest request) {
        syntaxParseSql(request.getStatement());
        String sql = JSONUtil.quote(request.getStatement(), false).replace("'", "\\'");
        String id = getSameJob(sql);
        if (StringUtil.isNotBlank(id)) {
            return BaseResultGenerator.successCreate(buildJobOfCreated(id));
        }
        request.getCustomAttributes().put("UUID", System.currentTimeMillis());
        id = buildJobId(request.toString());
        long currentTime = System.currentTimeMillis() / 1000;
        Db.update(String.format(Objects.requireNonNull(env.getProperty("SAVED_QUERY_JOB_INIT")), id, sql, currentTime, currentTime));
        return BaseResultGenerator.successCreate(buildJobOfCreated(id));
    }

    @Override
    public BaseResult commitSqlAdHoc(SqlQueryRequest request) {
        ExecModeEnum execMode = request.getExecMode();
        if (ExecModeEnum.ONESHOT.equals(execMode)) {
            BaseResult baseResult = queryService.executeQuery(QueryProfile.builder()
                    .query(request.getStatement())
                    .format(request.getOutputMode().getValue())
                    .build());
            request.getCustomAttributes().put("UUID", System.currentTimeMillis());
            String id = buildJobId(request.toString());
            baseResult.setJob(buildJobInfoOfExecSucceed(id));
            return baseResult;
        } else if (ExecModeEnum.NORMAL.equals(execMode)) {
            request.getCustomAttributes().put("UUID", System.currentTimeMillis());
            String id = buildJobId(request.toString());
            Map<String, Object> jobStatus = Maps.newHashMap();
            jobStatus.put(JobConfig.JOB_ID, id);
            QueryCache queryCache = new QueryCache();
            BaseResult baseResult = new BaseResult<>();
            baseResult.setJob(jobStatus);
            queryCache.setBaseResult(baseResult);
            HazelcastInstanceUtil.put(id, queryCache);
            jobExecuteService.addExecutorSql(id, request);
            return BaseResultGenerator.successCreate(buildJobOfCreated(id));
        } else if (ExecModeEnum.BLOCKING.equals(execMode)) {
            request.getCustomAttributes().put("UUID", System.currentTimeMillis());
            String id = buildJobId(request.toString());
            BaseResult baseResult = queryService.executeQuery(QueryProfile.builder()
                    .query(request.getStatement())
                    .format(request.getOutputMode().getValue())
                    .build());
            baseResult.setJob(buildJobInfoOfExecSucceed(id));
            QueryCache queryCache = new QueryCache();
            queryCache.setBaseResult(baseResult);
            HazelcastInstanceUtil.put(id, queryCache);
            return BaseResultGenerator.successCreate(buildJobOfCreated(id));
        }
        throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), QGWErrorCode.PARAM_SYNTAX_BAD_REQUEST_EXCEPTION.getCode(),
                String.format(QGWErrorCode.PARAM_SYNTAX_BAD_REQUEST_EXCEPTION.getMessage(), "not Supported"));
    }

    @Override
    public BaseResult commitDslAdHoc(DslQueryRequest request) {
        ExecModeEnum execMode = request.getExecMode();
        if (ExecModeEnum.ONESHOT.equals(execMode)) {
            DSLProfile dslProfile = BeanUtil.copyProperties(request, DSLProfile.class);
            BaseResult baseResult = dslController.post(dslProfile);
            request.getCustomAttributes().put("UUID", System.currentTimeMillis());
            String id = buildJobId(request.toString());
            baseResult.setJob(buildJobInfoOfExecSucceed(id));
            return baseResult;
        } else if (ExecModeEnum.NORMAL.equals(execMode)) {
            if (JobConfig.FIELD_DISCOVERY.equals(request.getName())) {
                HashMap<String, Object> map = Maps.newHashMap();
                map.put(JobConfig.KEY_QUERY_TYPE, JobConfig.FIELD_DISCOVERY);
                map.put(JobConfig.QUERY_DATA_SOURCE, request.getDataSource());
                map.put(JobConfig.KEY_CUSTOM_FIELD_DISCOVERY_FIELDS, request.getCustomAttributes().get(JobConfig.KEY_CUSTOM_FIELD_DISCOVERY_FIELDS));
                map.put("filter", request.getCustomAttributes().get(JobConfig.KEY_CUSTOM_FIELD_DISCOVERY_FILTER));
                map.put(JobConfig.KEY_CUSTOM_FIELD_DISCOVERY_METRIC, request.getCustomAttributes().get(JobConfig.KEY_CUSTOM_FIELD_DISCOVERY_METRIC));
                map.put(JobConfig.KEY_CUSTOM_FIELD_DISCOVERY_METRIC_FN, request.getCustomAttributes().get(JobConfig.KEY_CUSTOM_FIELD_DISCOVERY_METRIC_FN));
                return jobService.commitAdHocQuery(map);
            } else {
                DSLProfile dslProfile = BeanUtil.copyProperties(request, DSLProfile.class);
                BaseResult baseResult = dslController.post(dslProfile);
                request.getCustomAttributes().put("UUID", System.currentTimeMillis());
                String id = buildJobId(request.toString());
                baseResult.setJob(buildJobInfoOfExecSucceed(id));
                QueryCache queryCache = new QueryCache();
                queryCache.setBaseResult(baseResult);
                HazelcastInstanceUtil.put(id, queryCache);
                return BaseResultGenerator.successCreate(buildJobOfCreated(id));
            }
        }

        throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), QGWErrorCode.PARAM_SYNTAX_BAD_REQUEST_EXCEPTION.getCode(),
                String.format(QGWErrorCode.PARAM_SYNTAX_BAD_REQUEST_EXCEPTION.getMessage(), "not Supported"));
    }

    @Override
    public BaseResult adHocResult(String id) {
        QueryCache queryCache = HazelcastInstanceUtil.get(id);
        if (StrUtil.isEmptyIfStr(queryCache)) {
            return jobService.getResultFieldDiscovery(id);
        } else {
            return queryCache.getBaseResult();
        }
    }

    @Override
    public BaseResult savedQueryResult(String id) {
        return jobService.getSavedQueryResult(id);
    }

    @Override
    public BaseResult adHocStatus(String id) {
        QueryCache queryCache = HazelcastInstanceUtil.get(id);
        if (StrUtil.isEmptyIfStr(queryCache)) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), QGWErrorCode.PARAM_SYNTAX_BAD_REQUEST_EXCEPTION.getCode(),
                    String.format(QGWErrorCode.PARAM_SYNTAX_BAD_REQUEST_EXCEPTION.getMessage(), "not Supported"));
        } else {
            return queryCache.getBaseResult();
        }
    }

    @Override
    public BaseResult savedQueryStatus(String id) {
        return jobService.getSavedQueryStatus(id);
    }

    @Override
    public BaseResult savedQueryResultBatch(List<String> ids) {
        List<Map<String, Object>> result = jobService.batchSavedQueryResult(ids);
        return BaseResultGenerator.success(result);
    }

    @Override
    public BaseResult adHocResultBatch(List<String> ids) {
        List<Object> results = Lists.newArrayList();
        for (String id : ids) {
            QueryCache queryCache = HazelcastInstanceUtil.get(id);
            results.add(queryCache.getBaseResult());
        }
        return BaseResultGenerator.success(results);
    }

    @Override
    public BaseResult savedQueryStatusBatch(List<String> ids) {
        List<Map<String, Object>> result = jobService.batchSavedQueryStatus(ids);
        return BaseResultGenerator.success(result);
    }

    @Override
    public BaseResult adHocStatusBatch(List<String> ids) {
        List<Object> results = Lists.newArrayList();
        for (String id : ids) {
            QueryCache queryCache = HazelcastInstanceUtil.get(id);
            results.add(queryCache.getBaseResult().getJob());
        }
        return BaseResultGenerator.success(results);
    }

    private void syntaxParseSql(String sql) {
        QueryProfile queryProfile = QueryProfile.builder().query(sql)
                .option(QueryOptionEnum.SYNTAX_PARSE.getValue())
                .build();
        BaseResult baseResult = queryService.executeQuery(queryProfile);
        if (!baseResult.isSuccess()) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), baseResult.getMessage()));
        }
    }

    private String getSameJob(String paramSQL) {
        List<Record> jobs = Db.find(String.format(Objects.requireNonNull(env.getProperty("SAVED_QUERY_JOB_SAME")), paramSQL));
        if (jobs.isEmpty()) {
            return StringUtil.EMPTY;
        }
        return jobs.get(0).get("id");
    }

    private static String buildJobId(String request) {
        return DigestUtil.md5Hex(request);
    }

    private static Map<String, Object> buildJobOfCreated(String id) {
        Map<String, Object> result = Maps.newHashMap();
        result.put(JobConfig.JOB_ID, id);
        return result;
    }

    private static Map<String, Object> buildJobInfoOfExecSucceed(String id) {
        Map<String, Object> result = Maps.newLinkedHashMap();
        result.put(JobConfig.JOB_ID, id);
        result.put(JobConfig.IS_DONE, true);
        result.put(JobConfig.DONE_PROGRESS, 1);
        result.put(JobConfig.IS_CANCELED, false);
        result.put(JobConfig.IS_FAILED, false);
        Map<String, Object> links = Maps.newLinkedHashMap();
        links.put(JobConfig.LINKS_STATUS, "/v1/query/job/" + id);
        links.put(JobConfig.LINKS_RESULT, "/v1/query/job/" + id + "/result");
        result.put(JobConfig.LINKS, links);
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
    public void setDslController(DslController dslController) {
        this.dslController = dslController;
    }
}
