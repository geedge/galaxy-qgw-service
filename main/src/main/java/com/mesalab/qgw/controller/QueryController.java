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
    @CachedSubmitCheck
    public BaseResult get(QueryProfile queryProfile) {
        if (queryProfile.queryParamIsNull())
            return BaseResultGenerator.success4Message("ok");
        log.debug("Quest Params:{}", JSON.toJSONString(queryProfile));
        return queryService.executeQuery(queryProfile);
    }

    @PostMapping(value = "/sql", produces = "application/json;charset=utf-8")
    @AuditLog("QueryController.post")
    @CachedSubmitCheck
    public BaseResult post(@RequestBody QueryProfile queryProfile) {
        if (queryProfile.queryParamIsNull()) {
            return BaseResultGenerator.success4Message("ok");
        }
        log.debug("Quest Params:{}", JSON.toJSONString(queryProfile));
        return queryService.executeQuery(queryProfile);

    }

    @PostMapping(value = "/v1/query/sql", consumes = "application/json", produces = "application/json")
    public BaseResult commitSql(@RequestBody SqlQueryRequest request) {
        log.info("Create a SQL Query Job, params is: {}", JSON.toJSONString(request));
        if (request.isSavedQuery()) {
            if (request.isDryRun() || !request.getExecMode().equals(ExecModeEnum.NORMAL) || !request.getOutputMode().equals(OutputModeEnum.JSON)) {
                throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), QGWErrorCode.PARAM_SYNTAX_BAD_REQUEST_EXCEPTION.getCode(),
                        String.format(QGWErrorCode.PARAM_SYNTAX_BAD_REQUEST_EXCEPTION.getMessage(),
                                "for the Saved Query Job, is_dry_run must be 0(default), exec_mode must be normal(default), and output_mode must be JSON(default)"));
            }
            return queryJobService.commitSqlSavedQuery(request);
        } else {
            return queryJobService.commitSqlAdHoc(request);
        }
    }

    @PostMapping(value = "/v1/query/dsl", consumes = "application/json", produces = "application/json")
    public BaseResult commitDsl(@RequestBody DslQueryRequest request) {
        log.info("Create a DSL Query Job, params is: {}", JSON.toJSONString(request));
        if (request.isSavedQuery()) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), QGWErrorCode.PARAM_SYNTAX_BAD_REQUEST_EXCEPTION.getCode(),
                    String.format(QGWErrorCode.PARAM_SYNTAX_BAD_REQUEST_EXCEPTION.getMessage(), "The Saved Query Job does not yet support DSL."));
        } else {
            if (request.isDryRun() || !request.getOutputMode().equals(OutputModeEnum.JSON)) {
                throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), QGWErrorCode.PARAM_SYNTAX_BAD_REQUEST_EXCEPTION.getCode(),
                        String.format(QGWErrorCode.PARAM_SYNTAX_BAD_REQUEST_EXCEPTION.getMessage(), "The DSL AdHoc Query Job is_dry_run must be 0(default), output_mode must be JSON(default)"));
            }
            return queryJobService.commitDslAdHoc(request);
        }
    }

    @GetMapping(value = "/v1/query/job/{id}/result", consumes = "application/x-www-form-urlencoded")
    public BaseResult getResult(@PathVariable String id, @RequestParam(value = "is_saved_query", required = false, defaultValue = "0") Integer isSavedQuery) {
        if (BooleanUtil.toBoolean(String.valueOf(isSavedQuery))) {
            return queryJobService.getSavedQueryResult(id);
        } else {
            return queryJobService.getAdHocResult(id);
        }
    }

    @GetMapping(value = "/v1/query/job/{id}", consumes = "application/x-www-form-urlencoded")
    public BaseResult getStatus(@PathVariable String id, @RequestParam(value = "is_saved_query", required = false, defaultValue = "0") Integer isSavedQuery) {
        if (BooleanUtil.toBoolean(String.valueOf(isSavedQuery))) {
            return queryJobService.getSavedQueryStatus(id);
        } else {
            return queryJobService.getAdHocStatus(id);
        }
    }

    @GetMapping(value = "/v1/query/job/result", consumes = "application/x-www-form-urlencoded")
    public BaseResult getResultBatch(@RequestParam(value = "job_ids") String jobIds
            , @RequestParam(value = "is_saved_query", required = false, defaultValue = "0") Integer isSavedQueryInt) {
        boolean isSavedQuery = BooleanUtil.toBoolean(String.valueOf(isSavedQueryInt));
        List<String> ids = Arrays.stream(jobIds.split(",")).distinct().collect(Collectors.toList());
        if (isSavedQuery) {
            return queryJobService.getSavedQueryResultBatch(ids);
        } else {
            return queryJobService.getAdHocResultBatch(ids);
        }
    }

    @GetMapping(value = "/v1/query/job", consumes = "application/x-www-form-urlencoded")
    public BaseResult getStatusBatch(@RequestParam(value = "job_ids") String jobIds
            , @RequestParam(value = "is_saved_query", required = false, defaultValue = "0") Integer isSavedQueryInt) {
        boolean isSavedQuery = BooleanUtil.toBoolean(String.valueOf(isSavedQueryInt));
        List<String> ids = Arrays.stream(jobIds.split(",")).distinct().collect(Collectors.toList());
        if (isSavedQuery) {
            return queryJobService.getSavedQueryStatusBatch(ids);
        } else {
            return queryJobService.getAdHocStatusBatch(ids);
        }
    }

    @DeleteMapping(value = "/job/{id}", consumes = "application/x-www-form-urlencoded")
    public BaseResult cancelJob(@PathVariable String id, @RequestParam(value = "is_saved_query", required = false, defaultValue = "0") Integer isSavedQueryInt) {
        boolean isSavedQuery = BooleanUtil.toBoolean(String.valueOf(isSavedQueryInt));
        if (isSavedQuery) {
            return queryJobService.cancelSavedQuery(id);
        }
        throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), QGWErrorCode.PARAM_SYNTAX_BAD_REQUEST_EXCEPTION.getCode(),
                String.format(QGWErrorCode.PARAM_SYNTAX_BAD_REQUEST_EXCEPTION.getMessage(), "The AdHoc Query Job does not yet support deletion."));
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
