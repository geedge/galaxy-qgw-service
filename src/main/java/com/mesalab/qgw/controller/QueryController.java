package com.mesalab.qgw.controller;

import cn.hutool.core.util.BooleanUtil;
import com.alibaba.fastjson2.JSON;
import com.geedgenetworks.utils.StringUtil;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.ExecutionMode;
import com.mesalab.common.enums.OutputMode;
import com.mesalab.common.enums.HttpStatusCodeEnum;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.qgw.model.basic.*;
import com.mesalab.qgw.service.DeferredResultHolder;
import com.mesalab.qgw.service.QueryJobService;
import com.mesalab.qgw.service.SQLSyncQueryService;
import com.mesalab.services.configuration.JobConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Slf4j
@RestController
public class QueryController {

    private SQLSyncQueryService sqlSyncQueryService;
    private QueryJobService queryJobService;
    private JobConfig jobConfig;
    private DeferredResultHolder deferredResultHolder;

    @GetMapping("/sql")
    @AuditLog("QueryController.get")
    public BaseResult get(SQLQueryContext queryContext) {
        if (queryContext.originalSQLIsNull())
            return BaseResultGenerator.success4Message("ok");
        log.debug("Quest Params:{}", JSON.toJSONString(queryContext));
        return sqlSyncQueryService.executeQuery(queryContext);
    }

    @PostMapping(value = "/sql", produces = "application/json;charset=utf-8")
    @AuditLog("QueryController.post")
    public BaseResult post(@RequestBody SQLQueryContext queryContext) {
        if (queryContext.originalSQLIsNull()) {
            return BaseResultGenerator.success4Message("ok");
        }
        log.debug("Quest Params:{}", JSON.toJSONString(queryContext));
        return sqlSyncQueryService.executeQuery(queryContext);

    }

    @PostMapping(value = "/v1/query/sql", consumes = "application/json", produces = "application/json")
    @AuditLog("QueryController.createSQLQuery")
    public BaseResult createSQLQuery(@RequestBody SqlQueryRequestParam sqlQueryRequestParam) {
        log.info("Create an SQL Query {}", JSON.toJSONString(sqlQueryRequestParam));
        if (sqlQueryRequestParam.isSavedQuery()) {
            if (sqlQueryRequestParam.isDryRun() || !sqlQueryRequestParam.getExecutionMode().equals(ExecutionMode.NORMAL) || !sqlQueryRequestParam.getOutputMode().equals(OutputMode.JSON)) {
                throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getCode(),
                        String.format(CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getMessage(),
                                "for the Saved Query Job, is_dry_run must be 0(default), execution_mode must be normal(default), and output_mode must be JSON(default)"));
            }
            return queryJobService.createSQLSavedQuery(sqlQueryRequestParam);
        } else {
            return queryJobService.createSQLAdHocQuery(sqlQueryRequestParam);
        }
    }

    @PostMapping(value = "/v1/query/dsl", consumes = "application/json", produces = "application/json")
    @AuditLog("QueryController.createDSLQuery")
    public BaseResult createDSLQuery(@RequestBody DSLQueryRequestParam dslQueryRequestParam) {
        log.info("Create a DSL query {}", JSON.toJSONString(dslQueryRequestParam));
        if (dslQueryRequestParam.isSavedQuery()) {
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getMessage(), "Currently, the DSL Saved Query is not supported."));
        } else {
            if (!dslQueryRequestParam.getOutputMode().equals(OutputMode.JSON)) {
                throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getCode(),
                        String.format(CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getMessage(), "Currently, the DSL Query not support output_mode must be JSON(default)"));
            }
            return queryJobService.createDSLAdHocQuery(dslQueryRequestParam);
        }
    }

    @GetMapping(value = "/v1/query/job/{id}/result", consumes = "application/x-www-form-urlencoded")
    @AuditLog("QueryController.getJobResultById")
    public DeferredResult<BaseResult> getJobResultById(@PathVariable String id, @RequestParam(value = "is_saved_query", required = false, defaultValue = "0") Integer isSavedQuery) {
        BaseResult baseResult;
        if (BooleanUtil.toBoolean(String.valueOf(isSavedQuery))) {
            baseResult = queryJobService.getSavedQueryResultById(id);
        } else {
            baseResult = queryJobService.getAdHocQueryResultById(id);
        }
        DeferredResult<BaseResult> deferredResult =
                deferredResultHolder.newDeferredResult(id, jobConfig.getResponseTimeout(), baseResult);
        if (!jobConfig.isLongPollingEnabled()) {
            deferredResultHolder.handleDeferredData(deferredResult, baseResult);
            return deferredResult;
        }
        deferredResult.onTimeout(() -> {
            deferredResultHolder.handleDeferredData(deferredResult, BooleanUtil.toBoolean(String.valueOf(isSavedQuery)) ? queryJobService.getSavedQueryResultById(id) : queryJobService.getAdHocQueryResultById(id));
        });

        if (StringUtil.isNotEmpty(baseResult)
                && StringUtil.isNotEmpty(baseResult.getJob())
                && Boolean.valueOf(String.valueOf(baseResult.getJob().get(JobConfig.IS_DONE)))) {
            deferredResultHolder.handleDeferredData(deferredResult, baseResult);
        }
        return deferredResult;
    }

    @GetMapping(value = "/v1/query/job/{id}", consumes = "application/x-www-form-urlencoded")
    @AuditLog("QueryController.getJobStatusById")
    public DeferredResult<BaseResult> getJobStatusById(@PathVariable String id, @RequestParam(value = "is_saved_query", required = false, defaultValue = "0") Integer isSavedQuery) {
        BaseResult baseResult;
        if (BooleanUtil.toBoolean(String.valueOf(isSavedQuery))) {
            baseResult = queryJobService.getSavedQueryStatusById(id);
        } else {
            baseResult = queryJobService.getAdHocQueryStatusById(id);
        }
        DeferredResult<BaseResult> deferredResult =
                deferredResultHolder.newDeferredResult(id, jobConfig.getResponseTimeout(), baseResult);
        if (!jobConfig.isLongPollingEnabled()) {
            deferredResultHolder.handleDeferredData(deferredResult, baseResult);
            return deferredResult;
        }
        deferredResult.onTimeout(() -> {
            deferredResultHolder.handleDeferredData(deferredResult, BooleanUtil.toBoolean(String.valueOf(isSavedQuery)) ? queryJobService.getSavedQueryStatusById(id) : queryJobService.getAdHocQueryStatusById(id));
        });

        if (StringUtil.isNotEmpty(baseResult)
                && StringUtil.isNotEmpty(baseResult.getJob())
                && Boolean.valueOf(String.valueOf(baseResult.getJob().get(JobConfig.IS_DONE)))) {
            deferredResultHolder.handleDeferredData(deferredResult, baseResult);
        }
        return deferredResult;
    }

    @GetMapping(value = "/v1/query/job/result", consumes = "application/x-www-form-urlencoded")
    @AuditLog("QueryController.getJobResult")
    public DeferredResult<BaseResult> getJobResult(@RequestParam(value = "job_ids") String jobIds
            , @RequestParam(value = "is_saved_query", required = false, defaultValue = "0") Integer isSavedQueryInt) {
        boolean isSavedQuery = BooleanUtil.toBoolean(String.valueOf(isSavedQueryInt));
        List<String> ids = Arrays.stream(jobIds.split(",")).distinct().collect(Collectors.toList());
        BaseResult baseResult;
        if (isSavedQuery) {
            baseResult = queryJobService.getSavedQueryResult(ids);
        } else {
            baseResult = queryJobService.getAdHocQueryResult(ids);
        }

        DeferredResult<BaseResult> deferredResult =
                deferredResultHolder.newDeferredResult(String.valueOf(ids), jobConfig.getResponseTimeout(), baseResult);
        if (!jobConfig.isLongPollingEnabled()) {
            deferredResultHolder.handleDeferredData(deferredResult, baseResult);
            return deferredResult;
        }
        deferredResult.onTimeout(() -> {
            deferredResultHolder.handleDeferredData(deferredResult, BooleanUtil.toBoolean(String.valueOf(isSavedQuery)) ? queryJobService.getSavedQueryResult(ids) : queryJobService.getAdHocQueryResult(ids));
        });

        if (jobsIsDoneOfResult(baseResult)) {
            deferredResultHolder.handleDeferredData(deferredResult, baseResult);
        }
        return deferredResult;
    }

    @GetMapping(value = "/v1/query/job", consumes = "application/x-www-form-urlencoded")
    @AuditLog("QueryController.getJobStatus")
    public DeferredResult<BaseResult> getJobStatus(@RequestParam(value = "job_ids") String jobIds
            , @RequestParam(value = "is_saved_query", required = false, defaultValue = "0") Integer isSavedQueryInt) {
        boolean isSavedQuery = BooleanUtil.toBoolean(String.valueOf(isSavedQueryInt));
        List<String> ids = Arrays.stream(jobIds.split(",")).distinct().collect(Collectors.toList());
        BaseResult baseResult;
        if (isSavedQuery) {
            baseResult = queryJobService.getSavedQueryStatus(ids);
        } else {
            baseResult = queryJobService.getAdHocQueryStatus(ids);
        }

        DeferredResult<BaseResult> deferredResult =
                deferredResultHolder.newDeferredResult(String.valueOf(ids), jobConfig.getResponseTimeout(), baseResult);
        if (!jobConfig.isLongPollingEnabled()) {
            deferredResultHolder.handleDeferredData(deferredResult, baseResult);
            return deferredResult;
        }
        deferredResult.onTimeout(() -> {
            deferredResultHolder.handleDeferredData(deferredResult, BooleanUtil.toBoolean(String.valueOf(isSavedQuery)) ? queryJobService.getSavedQueryStatus(ids) : queryJobService.getAdHocQueryStatus(ids));
        });

        if (jobsIsDoneOfStatus(baseResult)) {
            deferredResultHolder.handleDeferredData(deferredResult, baseResult);
        }
        return deferredResult;
    }

    @DeleteMapping(value = "/v1/query/job/{id}", consumes = "application/x-www-form-urlencoded")
    @AuditLog("QueryController.deleteJobById")
    public BaseResult deleteJobById(@PathVariable String id, @RequestParam(value = "is_saved_query", required = false, defaultValue = "0") Integer isSavedQueryInt) {
        boolean isSavedQuery = BooleanUtil.toBoolean(String.valueOf(isSavedQueryInt));
        if (isSavedQuery) {
            return queryJobService.deleteSavedQueryById(id);
        }
        throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getCode(),
                String.format(CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getMessage(), "The AdHoc Query Job does not yet support deletion."));
    }

    private boolean jobsIsDoneOfResult(BaseResult baseResult) {
        if (StringUtil.isEmpty(baseResult)) {
            return false;
        }
        if (StringUtil.isEmpty(baseResult.getData())) {
            return false;
        }
        List<Map<String, Object>> data = (List<Map<String, Object>>) baseResult.getData();
        for (Map<String, Object> datum : data) {
            Map<String, Object> job = (Map<String, Object>) datum.get(JobConfig.JOB);
            if (!BooleanUtil.toBoolean(String.valueOf(job.get(JobConfig.IS_DONE)))) {
                return false;
            }
        }
        return true;
    }

    private boolean jobsIsDoneOfStatus(BaseResult baseResult) {
        if (StringUtil.isEmpty(baseResult)) {
            return false;
        }
        if (StringUtil.isEmpty(baseResult.getData())) {
            return false;
        }
        List<Map<String, Object>> data = (List<Map<String, Object>>) baseResult.getData();
        for (Map<String, Object> datum : data) {
            if (!BooleanUtil.toBoolean(String.valueOf(datum.get(JobConfig.IS_DONE)))) {
                return false;
            }
        }
        return true;
    }

    @Autowired
    public void setQueryJobService(QueryJobService queryJobService) {
        this.queryJobService = queryJobService;
    }

    @Autowired
    public void setSqlSyncQueryService(SQLSyncQueryService sqlSyncQueryService) {
        this.sqlSyncQueryService = sqlSyncQueryService;
    }

    @Autowired
    public void setJobConfig(JobConfig jobConfig) {
        this.jobConfig = jobConfig;
    }

    @Autowired
    public void setDeferredResultHolder(DeferredResultHolder deferredResultHolder) {
        this.deferredResultHolder = deferredResultHolder;
    }
}
