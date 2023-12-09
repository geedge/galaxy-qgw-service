package com.mesalab.qgw.controller;

import cn.hutool.core.util.BooleanUtil;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.enums.ExecModeEnum;
import com.mesalab.common.enums.OutputModeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.qgw.exception.QGWErrorCode;
import com.mesalab.qgw.model.basic.DslQueryRequest;
import com.mesalab.qgw.model.basic.SqlQueryRequest;
import com.mesalab.qgw.service.QueryJobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;


/**
 * TODO
 *
 * @Classname QueryJobController
 * @Date 2023/12/6 14:59
 * @Author wWei
 */
@RestController
@RequestMapping(value = "/v1/query")
public class QueryJobController {
    private QueryJobService queryJobService;

    @PostMapping(value = "/sql", consumes = "application/json", produces = "application/json")
    public BaseResult commitSql(@RequestBody SqlQueryRequest request) {
        if (request.isSavedQuery()) {
            if (request.isDryRun() || !request.getExecMode().equals(ExecModeEnum.NORMAL) || !request.getOutputMode().equals(OutputModeEnum.JSON)) {
                throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), QGWErrorCode.PARAM_SYNTAX_BAD_REQUEST_EXCEPTION.getCode(),
                        String.format(QGWErrorCode.PARAM_SYNTAX_BAD_REQUEST_EXCEPTION.getMessage(), "not Support"));
            }
            return queryJobService.commitSqlSavedQuery(request);
        } else {
            return queryJobService.commitSqlAdHoc(request);
        }
    }

    @PostMapping(value = "/dsl", consumes = "application/json", produces = "application/json")
    public BaseResult commitDsl(@RequestBody DslQueryRequest request) {
        if (request.isSavedQuery()) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), QGWErrorCode.PARAM_SYNTAX_BAD_REQUEST_EXCEPTION.getCode(),
                    String.format(QGWErrorCode.PARAM_SYNTAX_BAD_REQUEST_EXCEPTION.getMessage(), "not Support"));
        } else {
            if (request.isDryRun() || !request.getOutputMode().equals(OutputModeEnum.JSON)) {
                throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), QGWErrorCode.PARAM_SYNTAX_BAD_REQUEST_EXCEPTION.getCode(),
                        String.format(QGWErrorCode.PARAM_SYNTAX_BAD_REQUEST_EXCEPTION.getMessage(), "not Support"));
            }
            return queryJobService.commitDslAdHoc(request);
        }
    }

    @GetMapping(value = "/job/{id}/result", consumes = "application/x-www-form-urlencoded")
    public BaseResult result(@PathVariable String id, @RequestParam(value = "is_saved_query", required = false, defaultValue = "0") Integer isSavedQuery) {
        if (BooleanUtil.toBoolean(String.valueOf(isSavedQuery))) {
            return queryJobService.savedQueryResult(id);
        } else {
            return queryJobService.adHocResult(id);
        }
    }

    @GetMapping(value = "/job/{id}", consumes = "application/x-www-form-urlencoded")
    public BaseResult status(@PathVariable String id, @RequestParam(value = "is_saved_query", required = false, defaultValue = "0") Integer isSavedQuery) {
        if (BooleanUtil.toBoolean(String.valueOf(isSavedQuery))) {
            return queryJobService.savedQueryStatus(id);
        } else {
            return queryJobService.adHocStatus(id);
        }
    }

    @GetMapping(value = "/job/result", consumes = "application/x-www-form-urlencoded")
    public BaseResult resultBatch(@RequestParam(value = "job_ids") String jobIds
            , @RequestParam(value = "is_saved_query", required = false, defaultValue = "0") Integer isSavedQueryInt) {
        boolean isSavedQuery = BooleanUtil.toBoolean(String.valueOf(isSavedQueryInt));
        List<String> ids = Arrays.stream(jobIds.split(",")).distinct().collect(Collectors.toList());
        if (isSavedQuery) {
            return queryJobService.savedQueryResultBatch(ids);
        } else {
            return queryJobService.adHocResultBatch(ids);
        }
    }

    @GetMapping(value = "/job", consumes = "application/x-www-form-urlencoded")
    public BaseResult statusBatch(@RequestParam(value = "job_ids") String jobIds
            , @RequestParam(value = "is_saved_query", required = false, defaultValue = "0") Integer isSavedQueryInt) {
        boolean isSavedQuery = BooleanUtil.toBoolean(String.valueOf(isSavedQueryInt));
        List<String> ids = Arrays.stream(jobIds.split(",")).distinct().collect(Collectors.toList());
        if (isSavedQuery) {
            return queryJobService.savedQueryStatusBatch(ids);
        } else {
            return queryJobService.adHocStatusBatch(ids);
        }
    }

    @Autowired
    public void setQueryJobService(QueryJobService queryJobService) {
        this.queryJobService = queryJobService;
    }
}

