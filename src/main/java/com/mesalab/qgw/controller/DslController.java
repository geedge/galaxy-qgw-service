package com.mesalab.qgw.controller;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.qgw.constant.DslIdentifierNameConst;
import com.mesalab.qgw.model.basic.DSLProfile;
import com.mesalab.qgw.service.DslService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


/**
 * TODO
 *
 * @Classname DslController
 * @Date 2023/11/25 17:39
 * @Author wWei
 */
@RestController
@RequestMapping(value = "/dsl")
public class DslController {

    private static final Log log = LogFactory.get();

    @Autowired
    DslService dslService;

    @PostMapping(consumes = "application/json")
    public BaseResult post(@RequestBody DSLProfile dslProfile) {
        log.info("HTTP REST DSL, params is: {}", dslProfile);
        if (DslIdentifierNameConst.APPLICATION_AND_PROTOCOL_SUMMARY.equals(dslProfile.getName())) {
            return dslService.appAndProtocolSummary(dslProfile);
        } else if (DslIdentifierNameConst.APPLICATION_AND_PROTOCOL_TREE_COMPOSITION.equals(dslProfile.getName())) {
            return dslService.appAndProtocolTreeComposition(dslProfile);
        } else if (DslIdentifierNameConst.APPLICATION_AND_PROTOCOL_TREE_THROUGHPUT.equals(dslProfile.getName())) {
            return dslService.applicationAndProtocolTreeThroughput(dslProfile);
        } else if (DslIdentifierNameConst.APPLICATION_AND_PROTOCOL_TOP_APPS.equals(dslProfile.getName())) {
            return dslService.applicationAndProtocolTopApp(dslProfile);
        } else if (DslIdentifierNameConst.APPLICATION_AND_PROTOCOL_APP_RELATED_INTERNAL_IPS.equals(dslProfile.getName())) {
            return dslService.applicationAndProtocolAppRelatedInternalIps(dslProfile);
        } else if (DslIdentifierNameConst.APPLICATION_AND_PROTOCOL_APP_THROUGHPUT.equals(dslProfile.getName())) {
            return dslService.applicationAndProtocolAppThroughput(dslProfile);
        } else if (DslIdentifierNameConst.APPLICATION_AND_PROTOCOL_APP_SUMMARY.equals(dslProfile.getName())) {
            return dslService.applicationAndProtocolAppSummary(dslProfile);
        } else if (DslIdentifierNameConst.REAL_TIME_DATA_ANALYTICS_SUBSCRIBER_ID_RELATE_IP.equals(dslProfile.getName())) {
            return dslService.realTimeDataAnalyticsSubscriberIdRelateIp(dslProfile);
        } else if (DslIdentifierNameConst.REAL_TIME_DATA_ANALYTICS_MOBILE_IDENTITY_RELATE_TEID.equals(dslProfile.getName())) {
            return dslService.realTimeDataAnalyticsMobileIdentityRelateTeid(dslProfile);
        }
        throw new BusinessException(ResultStatusEnum.NOT_FOUND.getCode(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(), dslProfile.getName()));
    }
}