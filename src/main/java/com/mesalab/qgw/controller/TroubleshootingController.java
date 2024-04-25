package com.mesalab.qgw.controller;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpStatus;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.enums.*;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.qgw.model.basic.AuditLog;
import com.mesalab.qgw.service.TroubleshootingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

/**
 * Galaxy日志自动诊断接口
 */
@Slf4j
@RestController
@RequestMapping(value = "/v1/troubleshooting")
public class TroubleshootingController {

    @Autowired
    private TroubleshootingService troubleshootingService;

    @RequestMapping("/component/status")
    @AuditLog("TroubleshootingController.componentStatus")
    public JSONObject componentStatus() {
        return troubleshootingService.getComponentStatus();
    }

    @RequestMapping(value = "sanity", method = RequestMethod.GET)
    @AuditLog("TroubleshootingController.sanity")
    public BaseResult sanity(String test) {
        if (Arrays.stream(ConsistencyOption.values()).noneMatch(o -> o.getValue().equalsIgnoreCase(test))) {
            throw new QGWBusinessException(HttpStatus.HTTP_BAD_REQUEST, CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.CONSISTENCY_OPTION_ERROR));
        }
        if (ConsistencyOption.TTL_CONSISTENCY.getValue().equalsIgnoreCase(test)) {
            log.info("ttl consistency check interface.");
            return troubleshootingService.consistencyCheck();
        } else if (ConsistencyOption.DATASET_VERIFICATION.getValue().equalsIgnoreCase(test)) {
            log.info("dataset verification interface. ");
            return troubleshootingService.datesetVerification();
        } else {
            log.info("schema consistency check interface.");
            return troubleshootingService.validateMetadata();
        }
    }

    @RequestMapping(value = "/benchmark", method = RequestMethod.GET)
    @AuditLog("TroubleshootingController.benchmark")
    public BaseResult benchmark(@RequestParam String test, @RequestParam(value = "is_saved", required = false, defaultValue = "0") @JsonProperty("isSaved") Integer isSaved) throws BusinessException {
        if (StrUtil.isBlankIfStr(test)) {
            throw new QGWBusinessException(HttpStatus.HTTP_BAD_REQUEST, CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.BENCHMARK_TEST_PARAM_CANNOT_BE_EMPTY));
        }
        return troubleshootingService.benchmarkTest(test, BooleanUtil.toBoolean(String.valueOf(isSaved)));
    }

}
