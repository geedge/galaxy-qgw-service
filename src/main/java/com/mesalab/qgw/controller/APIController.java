package com.mesalab.qgw.controller;

import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.BaseResultEnum;
import com.mesalab.common.enums.QueryOptionEnum;
import com.mesalab.common.exception.BusinessException;
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

import java.util.Map;

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

    @ApiOperation(value = "HTTP SCHEMA 查询", httpMethod = "GET", notes = "元数据管理，用于描述数据的数据。包含数据库表结构，支持那些数据表，业务规则定义等。")
    @RequestMapping("/metadata/schema/v1/{type}/{name}")
    @CachedSubmitCheck
    public BaseResult getSchema(@PathVariable String type, @PathVariable String name) {
        log.debug("SCHEMA信息获取,参数为{} {}", type, name);
        if (StringUtil.isBlank(type) || StringUtil.isBlank(name)) {
            return BaseResultGenerator.success4Message("ok");
        }
        ApiParam param = new ApiParam();
        param.setOption(QueryOptionEnum.QUERY_SCHEMA.getValue());
        if("tables".equals(type)){
            param.setSchema(name);
            param.setQuery("show tables");
        }else if("fields".equals(type)){
            param.setTableName(name);
            param.setQuery("DESCRIBE " + name);
        }else {
            throw new BusinessException(BaseResultEnum.NOT_FOUND.getCode(), "Not found, check url please", null);
        }
        return apiService.executeQuery(param);
    }

    @ApiOperation(value = "HTTP SCHEMA 查询", httpMethod = "GET", notes = "元数据管理，用于描述数据的数据。包含数据库表结构，支持那些数据表，业务规则定义等。")
    @RequestMapping("/sys/storage/quota")
    @CachedSubmitCheck
    public BaseResult storageQuota(){
        ApiParam param = new ApiParam();
        param.setQuery("(select log_type, used_size, max_size from sys_storage_log where log_type = 'Analytic_Log' ORDER BY __time desc limit 1)\n" +
                "UNION ALL\n" +
                "(select log_type, used_size, max_size from sys_storage_log where log_type = 'Files' ORDER BY __time desc limit 1)\n" +
                "UNION ALL\n" +
                "(select log_type, used_size, max_size from sys_storage_log where log_type = 'Traffic_Log' ORDER BY __time desc limit 1)");
        return apiService.executeQuery(param);
    }
}
