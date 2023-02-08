package com.measlab.tsg.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.collect.Lists;
import com.measlab.tsg.entity.DSLValidate;
import com.measlab.tsg.entity.NetWorkDSLObject;
import com.measlab.tsg.service.NetworkMonitorService;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;


/**
 * @author wangwei
 * @version 1.0
 * @date 2020/6/30 10:38 上午
 */
@RestController
@RequestMapping(value = "/")
public class NetworkMonitorController {
    private static final Log log = LogFactory.get();
    @Autowired
    private DSLValidate dslValidate;
    @Autowired
    private NetworkMonitorService networkMonitorService;
    private static final String PROTOCOL = "protocol";
    private static final String APP = "app";

    @PostMapping(value = "/traffic/v1/", produces = "application/json")
    public BaseResult trafficStatistics(HttpServletRequest request, @Validated @RequestBody NetWorkDSLObject dslObject) {
        Enumeration<String> parameterNames = request.getParameterNames();
        List<String> params = Lists.newArrayList();
        while (parameterNames.hasMoreElements()) {
            params.add(StrUtil.trim(parameterNames.nextElement()).toLowerCase(Locale.ROOT));
        }
        if (params.contains(PROTOCOL)) {
            log.debug("network protocol interface, param: queryString is {}, body is {}.", params, dslObject);
            dslValidate.executeValidate(dslObject);
            return protocolData(dslObject);
        } else if (params.contains(APP)) {
            log.debug("network app interface, param: queryString is {}, body is {}.", params, dslObject);
            dslValidate.executeValidate(dslObject);
            return appData(dslObject);
        } else {
            throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(), "not support query parameter");
        }
    }

    private BaseResult appData(NetWorkDSLObject dslObject) {
        if (QueryType.APP_DATA_SUMMARY.getValue()
                .equalsIgnoreCase(dslObject.getQuery().getQueryType())) {
            return networkMonitorService.buildAppData(dslObject);
        } else if (QueryType.INTERNAL_IP_DATA_SUMMARY.getValue()
                .equalsIgnoreCase(dslObject.getQuery().getQueryType())) {
            return networkMonitorService.buildInternalIPData(dslObject);
        } else {
            throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(), "No match queryType");
        }
    }

    private BaseResult protocolData(NetWorkDSLObject dslObject) {
        if (QueryType.PROTOCOL_TREE_SUMMARY.getValue()
                .equalsIgnoreCase(dslObject.getQuery().getQueryType())) {
            return networkMonitorService.buildProtocolTree(dslObject);
        } else if (QueryType.PROTOCOL_DATA_RATE_SUMMARY.getValue()
                .equalsIgnoreCase(dslObject.getQuery().getQueryType())) {
            return networkMonitorService.buildProtocolDataRateSummary(dslObject);
        } else if (QueryType.NETWORK_OVERVIEW_SUMMARY.getValue().equalsIgnoreCase(dslObject.getQuery().getQueryType())) {
            return networkMonitorService.buildNetworkOverviewResult(dslObject);
        } else {
            return BaseResultGenerator.failure(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(), "No match queryType");
        }
    }

    enum QueryType {
        PROTOCOL_TREE_SUMMARY("protocolTreeSummary"),
        PROTOCOL_DATA_RATE_SUMMARY("protocolDataRateSummary"),
        NETWORK_OVERVIEW_SUMMARY("networkOverviewSummary"),
        APP_DATA_SUMMARY("appDataSummary"),
        INTERNAL_IP_DATA_SUMMARY("internalIPDataSummary");
        private final String value;

        QueryType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
