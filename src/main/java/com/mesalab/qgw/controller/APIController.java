package com.mesalab.qgw.controller;

import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.AuditLog;
import com.mesalab.qgw.model.api.CachedSubmitCheck;
import com.mesalab.qgw.service.IAPIService;
import com.zdjizhi.utils.StringUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


@Slf4j
@Api(value = "/", tags = "银河数据平台-统一接入网关")
@RestController
@RequestMapping(value = "/")
public class APIController {

    @Autowired
    private IAPIService apiService;

    @ApiOperation(value = "HTTP SQL 查询", httpMethod = "GET", notes = "输入SQL语句,返回查询结果")
    @GetMapping(value = "/")
    @AuditLog("QGW GET SERVICE")
    @CachedSubmitCheck
    public BaseResult queryByGet(ApiParam param) {

        log.debug("GET Quest Params  option:{}, format:{}, SQL:{}",  param.getOption(), param.getFormat(), param.getQuery());

        if (StringUtil.isBlank(param.getQuery())) {

            return BaseResultGenerator.success4Message("ok");
        }
        return apiService.executeQuery(param);

    }

    @ApiOperation(value = "HTTP SQL 查询", httpMethod = "POST", notes = "输入SQL语句,返回查询结果")
    @PostMapping(value = "/", produces = "application/json")
    @AuditLog("QGW POST SERVICE")
    @CachedSubmitCheck
    public BaseResult queryByPost(ApiParam param) {

        log.debug("POST接收查询参数 option:{}, format:{}, SQL:{}",  param.getOption(), param.getFormat(), param.getQuery());

        if (StringUtil.isBlank(param.getQuery())) {

            return BaseResultGenerator.success4Message("ok");
        }

        return apiService.executeQuery(param);

    }
}
