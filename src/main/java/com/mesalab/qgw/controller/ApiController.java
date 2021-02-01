package com.mesalab.qgw.controller;

import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.AuditLog;
import com.mesalab.qgw.model.api.CachedSubmitCheck;
import com.mesalab.qgw.service.ApiService;
import com.zdjizhi.utils.JsonMapper;
import com.zdjizhi.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@Slf4j
@RestController
@RequestMapping(value = "/")
public class ApiController {

    @Autowired
    private ApiService apiService;

    @RequestMapping("/")
    @AuditLog("QGW QUERY SERVICE")
    @CachedSubmitCheck
    public BaseResult query(ApiParam apiParam) {

        log.debug("Quest Params:{}", JsonMapper.toJsonString(apiParam));

        if (StringUtil.isBlank(apiParam.getQuery()))
            return BaseResultGenerator.success4Message("ok");

        return apiService.executeQuery(apiParam);
    }
}
