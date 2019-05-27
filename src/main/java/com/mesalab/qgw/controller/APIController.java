package com.mesalab.qgw.controller;

import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.qgw.model.api.QueryParam;
import com.mesalab.qgw.service.IAPIService;
import com.zdjizhi.utils.StringUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Api(value = "/", tags = "银河数据平台统一接入网关API")
@RestController
@RequestMapping(value = "/")
public class APIController {

    @Autowired
    private IAPIService apiService;

    @ApiOperation(value = "HTTP GET SQL 查询", httpMethod = "GET", notes = "统一HTTP GET SQL查询")
    @GetMapping(value = "/")
    public BaseResult queryByGet( QueryParam param) {
        log.info("GET接收查询参数SQL:{}, option:{}, format:{}", param.getQuery(), param.getOption(), param.getFormat());

        if (StringUtil.isBlank(param.getQuery())) {
            return BaseResultGenerator.success4Message("ok");
        }
        log.info("SQL 查询KEY {}", StringUtil.md5(param.getQuery()) );
        return apiService.executeQuery(param);

    }

    @ApiOperation(value = "HTTP Post SQL 查询", httpMethod = "POST", notes = "统一HTTP Post SQL查询")
    @PostMapping(value = "/", produces = "application/json")
    public BaseResult queryByPost(QueryParam param) {
        log.info("Post接收查询参数SQL:{}, option:{}, format:{}", param.getQuery(), param.getOption(), param.getFormat());
        if (StringUtil.isBlank(param.getQuery())) {
            return BaseResultGenerator.success4Message("ok");
        }

        return apiService.executeQuery(param);

    }



}
