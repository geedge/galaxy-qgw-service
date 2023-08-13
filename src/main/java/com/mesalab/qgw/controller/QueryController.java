package com.mesalab.qgw.controller;

import com.alibaba.fastjson2.JSON;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.qgw.model.basic.QueryProfile;
import com.mesalab.qgw.model.basic.AuditLog;
import com.mesalab.qgw.model.basic.CachedSubmitCheck;
import com.mesalab.qgw.service.QueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


@Slf4j
@RestController
@RequestMapping(value = "/")
public class QueryController {

    @Autowired
    private QueryService queryService;

    @GetMapping(value="/")
    @AuditLog("QueryController.get")
    @CachedSubmitCheck
    public BaseResult get(QueryProfile queryProfile) {
        if (queryProfile.queryParamIsNull())
            return BaseResultGenerator.success4Message("ok");
        log.debug("Quest Params:{}", JSON.toJSONString(queryProfile));
        return queryService.executeQuery(queryProfile);
    }

    @PostMapping(value = "/",
            produces = "application/json;charset=utf-8")
    @AuditLog("QueryController.post")
    @CachedSubmitCheck
    public BaseResult post(@RequestBody QueryProfile queryProfile) {
        if (queryProfile.queryParamIsNull()) {
            return BaseResultGenerator.success4Message("ok");
        }
        log.debug("Quest Params:{}", JSON.toJSONString(queryProfile));
        return queryService.executeQuery(queryProfile);

    }
}
