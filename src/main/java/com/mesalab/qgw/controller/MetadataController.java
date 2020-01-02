package com.mesalab.qgw.controller;

import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.BaseResultEnum;
import com.mesalab.common.enums.QueryOptionEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.CachedSubmitCheck;
import com.mesalab.qgw.service.IAPIService;
import com.mesalab.qgw.service.MetadataService;
import com.zdjizhi.utils.StringUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Api(value = "/", tags = "元数据相关接口")
@RestController
@RequestMapping(value = "/metadata")
public class MetadataController {

    @Autowired
    private MetadataService metadataService;


    @ApiOperation(value = "HTTP SCHEMA 查询", httpMethod = "GET", notes = "元数据管理，用于描述数据的数据。包含数据库表结构，支持那些数据表，业务规则定义等。")
    @RequestMapping("/schema/v1/{type}/{name}")
    @CachedSubmitCheck
    public BaseResult getSchema(@PathVariable String type, @PathVariable String name) {
        log.debug("SCHEMA信息获取,参数为{} {}", type, name);

        if (StringUtil.isBlank(type) || StringUtil.isBlank(name)) {

            return BaseResultGenerator.success4Message("ok");
        }
        return metadataService.getSchemaInfo(type, name);
    }
}
