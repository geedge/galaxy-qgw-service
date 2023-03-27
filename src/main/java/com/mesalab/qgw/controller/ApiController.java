package com.mesalab.qgw.controller;

import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.AuditLog;
import com.mesalab.qgw.model.api.CachedSubmitCheck;
import com.mesalab.qgw.service.ApiService;
import com.zdjizhi.utils.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


@Slf4j
@RestController
@RequestMapping(value = "/")
public class ApiController {

    @Autowired
    private ApiService apiService;

    @GetMapping(value="/")
    @AuditLog("ApiController.get")
    @CachedSubmitCheck
    public BaseResult get(ApiParam apiParam) {
        if (apiParam.queryParamIsNull())
            return BaseResultGenerator.success4Message("ok");
        log.debug("Quest Params:{}", JsonMapper.toJsonString(apiParam));
        return apiService.executeQuery(apiParam);
    }

    @PostMapping(value = "/",
            produces = "application/json;charset=utf-8")
    @AuditLog("ApiController.post")
    @CachedSubmitCheck
    public BaseResult post(@RequestBody ApiParam apiParam) {
        if (apiParam.queryParamIsNull()) {
            return BaseResultGenerator.success4Message("ok");
        }
        log.debug("Quest Params:{}", JsonMapper.toJsonString(apiParam));
        return apiService.executeQuery(apiParam);

    }
}
