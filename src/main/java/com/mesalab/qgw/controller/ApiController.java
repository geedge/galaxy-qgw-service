package com.mesalab.qgw.controller;

import com.google.gson.Gson;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.AuditLog;
import com.mesalab.qgw.model.api.CachedSubmitCheck;
import com.mesalab.qgw.service.ApiService;
import com.zdjizhi.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@Slf4j
@RestController
@RequestMapping(value = "/")
public class ApiController {

    @Autowired
    ApiService apiService;

    @RequestMapping("/")
    @AuditLog("QGW QUERY SERVICE")
    @CachedSubmitCheck
    public BaseResult querey(ApiParam apiParam) {

        log.debug("Quest Params:{}",new Gson().toJson(apiParam));

        if (StringUtil.isBlank(apiParam.getQuery()))
            return BaseResultGenerator.success4Message("ok");

        return apiService.executeQuery(apiParam);
    }

    /**
     * @Description: APP-ID字典列表
     * @Author: liuyongqiang
     * @Date: 2020/9/17 15:32
     * @return: com.mesalab.common.base.BaseResult
     **/
    @GetMapping("appIdDicList")
    public BaseResult appIdDicList(){
        return apiService.appIdDicList();
    }
}
