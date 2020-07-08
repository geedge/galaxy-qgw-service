package com.mesalab.network.controller;

import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.network.model.api.Param;
import com.mesalab.network.service.NetworkMonitorService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;


/**
 * @author gouge
 * @version 1.0
 * @date 2020/6/30 10:38 上午
 */
@Slf4j
@Api(value = "/", tags = "LiveCharts")
@RestController
@RequestMapping(value = "/")
public class NetworkMonitorController {

    @Autowired
    private NetworkMonitorService networkMonitorService;
    private static final String PROTOCOL = "protocol";

    @ApiOperation(value = "流量分布接口", httpMethod = "POST", notes = "流量分布接口")
    @PostMapping(value = "/traffic/v1/", produces = "application/json")
    public BaseResult trafficDistribution(HttpServletRequest request, @Validated @RequestBody Param param) {
        if (PROTOCOL.equalsIgnoreCase(request.getQueryString())) {
            log.info("流量分布接口, 参数: queryString is {},params is {}", request.getQueryString(), param);
            return networkMonitorService.getTrafficDistributedInfo(param);
        } else {
            return BaseResultGenerator.failure("Support protocol Only , Now");
        }
    }
}
