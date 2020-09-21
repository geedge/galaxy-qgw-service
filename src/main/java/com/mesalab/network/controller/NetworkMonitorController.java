package com.mesalab.network.controller;

import com.mesalab.common.base.BaseResult;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.network.dsl.DSLObject;
import com.mesalab.network.dsl.DSLValidate;
import com.mesalab.network.service.NetworkMonitorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;


/**
 * @author wangwei
 * @version 1.0
 * @date 2020/6/30 10:38 上午
 */
@Slf4j
@RestController
@RequestMapping(value = "/")
public class NetworkMonitorController {

    @Autowired
    private DSLValidate dslValidate;
    @Autowired
    private NetworkMonitorService networkMonitorService;
    private static final String PROTOCOL = "protocol";

    @PostMapping(value = "/traffic/v1/", produces = "application/json")
    public BaseResult trafficDistribution(HttpServletRequest request, @Validated @RequestBody DSLObject dslObject) {

        if (PROTOCOL.equalsIgnoreCase(request.getQueryString())) {
            log.info("流量分布接口, 参数: queryString is {},params is {}", request.getQueryString(), dslObject);
            dslValidate.executeValidate(dslObject);
            return networkMonitorService.getTrafficDistributedInfo(dslObject);
        } else {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(), "Support protocol only", null);
        }
    }
}
