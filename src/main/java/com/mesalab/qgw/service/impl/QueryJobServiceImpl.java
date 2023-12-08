package com.mesalab.qgw.service.impl;

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
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.qgw.exception.QGWErrorCode;
import com.mesalab.qgw.model.basic.DslQueryRequest;
import com.mesalab.qgw.model.basic.QueryCache;
import com.mesalab.qgw.model.basic.QueryProfile;
import com.mesalab.qgw.model.basic.SqlQueryRequest;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    @Override
    public BaseResult commitSqlSavedQuery(SqlQueryRequest request) {
        syntaxParseSql(request.getStatement());
        String sql = JSONUtil.quote(request.getStatement(), false).replace("'", "\\'");
        String id = getSameJob(sql);
        if (StringUtil.isNotBlank(id)) {
            return BaseResultGenerator.successCreate(buildJobResource(id));
        }
        request.getCustomAttributes().put("UUID", System.currentTimeMillis());
        id = DigestUtil.md5Hex(request.toString());
        long currentTime = System.currentTimeMillis() / 1000;
        Db.update(String.format(Objects.requireNonNull(env.getProperty("SAVED_QUERY_JOB_INIT")), id, sql, currentTime, currentTime));
        return BaseResultGenerator.successCreate(buildJobResource(id));
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
            String id = DigestUtil.md5Hex16(String.valueOf(request.toString()));
            Map<String, Object> jobStatus = Maps.newHashMap();
            jobStatus.put(JobConfig.JOB_ID, id);
            jobStatus.put(JobConfig.IS_DONE, true);
            jobStatus.put(JobConfig.IS_FAILED, false);
            jobStatus.put(JobConfig.DONE_PROGRESS, 1);
            baseResult.setJob(jobStatus);
            return baseResult;
        } else if (ExecModeEnum.NORMAL.equals(execMode)) {
            request.getCustomAttributes().put("UUID", System.currentTimeMillis());
            String id = DigestUtil.md5Hex16(String.valueOf(request.toString()));
            Map<String, Object> jobStatus = Maps.newHashMap();
            jobStatus.put(JobConfig.JOB_ID, id);
            QueryCache queryCache = new QueryCache();
            BaseResult baseResult = new BaseResult<>();
            baseResult.setJob(jobStatus);
            queryCache.setBaseResult(baseResult);
            HazelcastInstanceUtil.put(id, queryCache);
            jobExecuteService.addExecutorSql(id, request);
            return BaseResultGenerator.successCreate(buildJobResource(id));
        } else if (ExecModeEnum.BLOCKING.equals(execMode)) {
            request.getCustomAttributes().put("UUID", System.currentTimeMillis());
            String id = DigestUtil.md5Hex16(String.valueOf(request.toString()));
            Map<String, Object> jobStatus = Maps.newHashMap();
            jobStatus.put(JobConfig.JOB_ID, id);
            jobStatus.put(JobConfig.IS_DONE, true);
            jobStatus.put(JobConfig.IS_FAILED, false);
            jobStatus.put(JobConfig.DONE_PROGRESS, 1);
            BaseResult baseResult = queryService.executeQuery(QueryProfile.builder()
                    .query(request.getStatement())
                    .format(request.getOutputMode().getValue())
                    .build());
            baseResult.setJob(jobStatus);
            QueryCache queryCache = new QueryCache();
            queryCache.setBaseResult(baseResult);
            HazelcastInstanceUtil.put(id, queryCache);
            return BaseResultGenerator.successCreate(buildJobResource(id));
        }
        throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), QGWErrorCode.PARAM_SYNTAX_BAD_REQUEST_EXCEPTION.getCode(),
                String.format(QGWErrorCode.PARAM_SYNTAX_BAD_REQUEST_EXCEPTION.getMessage(), "not Supported"));
    }


    @Override
    public BaseResult adHocResult(String id) {
        QueryCache queryCache = HazelcastInstanceUtil.get(id);
        return queryCache.getBaseResult();
    }

    @Override
    public BaseResult savedQueryResult(String id) {
        return jobService.getSavedQueryResult(id);
    }

    @Override
    public BaseResult adHocStatus(String id) {
        QueryCache queryCache = HazelcastInstanceUtil.get(id);
        return queryCache.getBaseResult();
    }

    @Override
    public BaseResult savedQueryStatus(String id) {
        return jobService.getSavedQueryStatus(id);
    }


    @Override
    public BaseResult commitDslAdHoc(DslQueryRequest request) {

        return null;
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


    private Map<String, Object> buildJobResource(String id) {
        Map<String, Object> result = Maps.newHashMap();
        result.put(JobConfig.JOB_ID, id);
        return result;
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
}
