package com.mesalab.qgw.controller;

import cn.hutool.core.util.BooleanUtil;
import com.alibaba.fastjson2.JSON;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.ExecModeEnum;
import com.mesalab.common.enums.OutputModeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.qgw.exception.QGWErrorCode;
import com.mesalab.qgw.model.basic.*;
import com.mesalab.qgw.service.QueryJobService;
import com.mesalab.qgw.service.QueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


@Slf4j
@RestController
public class QueryController {

    private QueryService queryService;
    private QueryJobService queryJobService;

    @GetMapping("/sql")
    @AuditLog("QueryController.get")
    public BaseResult get(QueryContext queryProfile) {
        if (queryProfile.queryParamIsNull())
            return BaseResultGenerator.success4Message("ok");
        log.debug("Quest Params:{}", JSON.toJSONString(queryProfile));
        return queryService.executeQuery(queryProfile);
    }

    @PostMapping(value = "/sql", produces = "application/json;charset=utf-8")
    @AuditLog("QueryController.post")
    public BaseResult post(@RequestBody QueryContext queryProfile) {
        if (queryProfile.queryParamIsNull()) {
            return BaseResultGenerator.success4Message("ok");
        }
        log.debug("Quest Params:{}", JSON.toJSONString(queryProfile));
        return queryService.executeQuery(queryProfile);

    }

    @PostMapping(value = "/v1/query/sql", consumes = "application/json", produces = "application/json")
    @AuditLog("QueryController.createSQLQuery")
    public BaseResult createSQLQuery(@RequestBody SqlQueryRequestParam sqlQueryRequestParam) {
        log.info("Create an SQL Query {}", JSON.toJSONString(sqlQueryRequestParam));
        if (sqlQueryRequestParam.isSavedQuery()) {
            if (sqlQueryRequestParam.isDryRun() || !sqlQueryRequestParam.getExecMode().equals(ExecModeEnum.NORMAL) || !sqlQueryRequestParam.getOutputMode().equals(OutputModeEnum.JSON)) {
                throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), QGWErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getCode(),
                        String.format(QGWErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getMessage(),
                                "for the Saved Query Job, is_dry_run must be 0(default), exec_mode must be normal(default), and output_mode must be JSON(default)"));
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
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), QGWErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getCode(),
                    String.format(QGWErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getMessage(), "Currently, the DSL Saved Query is not supported."));
        } else {
            if (dslQueryRequestParam.isDryRun() || !dslQueryRequestParam.getOutputMode().equals(OutputModeEnum.JSON)) {
                throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), QGWErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getCode(),
                        String.format(QGWErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getMessage(), "Currently, the DSL Query not support is_dry_run and output_mode must be 0(default) and JSON(default)"));
            }
            return queryJobService.createDSLAdHocQuery(dslQueryRequestParam);
        }
    }

    @GetMapping(value = "/v1/query/job/{id}/result", consumes = "application/x-www-form-urlencoded")
    @AuditLog("QueryController.getJobResultById")
    public BaseResult getJobResultById(@PathVariable String id, @RequestParam(value = "is_saved_query", required = false, defaultValue = "0") Integer isSavedQuery) {
        if (BooleanUtil.toBoolean(String.valueOf(isSavedQuery))) {
            return queryJobService.getSavedQueryResultById(id);
        } else {
            return queryJobService.getAdHocQueryResultById(id);
        }
    }

    @GetMapping(value = "/v1/query/job/{id}", consumes = "application/x-www-form-urlencoded")
    @AuditLog("QueryController.getJobStatusById")
    public BaseResult getJobStatusById(@PathVariable String id, @RequestParam(value = "is_saved_query", required = false, defaultValue = "0") Integer isSavedQuery) {
        if (BooleanUtil.toBoolean(String.valueOf(isSavedQuery))) {
            return queryJobService.getSavedQueryStatusById(id);
        } else {
            return queryJobService.getAdHocQueryStatusById(id);
        }
    }

    @GetMapping(value = "/v1/query/job/result", consumes = "application/x-www-form-urlencoded")
    @AuditLog("QueryController.getJobResult")
    public BaseResult getJobResult(@RequestParam(value = "job_ids") String jobIds
            , @RequestParam(value = "is_saved_query", required = false, defaultValue = "0") Integer isSavedQueryInt) {
        boolean isSavedQuery = BooleanUtil.toBoolean(String.valueOf(isSavedQueryInt));
        List<String> ids = Arrays.stream(jobIds.split(",")).distinct().collect(Collectors.toList());
        if (isSavedQuery) {
            return queryJobService.getSavedQueryResult(ids);
        } else {
            return queryJobService.getAdHocQueryResult(ids);
        }
    }

    @GetMapping(value = "/v1/query/job", consumes = "application/x-www-form-urlencoded")
    @AuditLog("QueryController.getJobStatus")
    public BaseResult getJobStatus(@RequestParam(value = "job_ids") String jobIds
            , @RequestParam(value = "is_saved_query", required = false, defaultValue = "0") Integer isSavedQueryInt) {
        boolean isSavedQuery = BooleanUtil.toBoolean(String.valueOf(isSavedQueryInt));
        List<String> ids = Arrays.stream(jobIds.split(",")).distinct().collect(Collectors.toList());
        if (isSavedQuery) {
            return queryJobService.getSavedQueryStatus(ids);
        } else {
            return queryJobService.getAdHocQueryStatus(ids);
        }
    }

    @DeleteMapping(value = "/v1/query/job/{id}", consumes = "application/x-www-form-urlencoded")
    @AuditLog("QueryController.deleteJobById")
    public BaseResult deleteJobById(@PathVariable String id, @RequestParam(value = "is_saved_query", required = false, defaultValue = "0") Integer isSavedQueryInt) {
        boolean isSavedQuery = BooleanUtil.toBoolean(String.valueOf(isSavedQueryInt));
        if (isSavedQuery) {
            return queryJobService.deleteSavedQueryById(id);
        }
        throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), QGWErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getCode(),
                String.format(QGWErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getMessage(), "The AdHoc Query Job does not yet support deletion."));
    }

    @Autowired
    public void setQueryJobService(QueryJobService queryJobService) {
        this.queryJobService = queryJobService;
    }

    @Autowired
    public void setQueryService(QueryService queryService) {
        this.queryService = queryService;
    }
}
