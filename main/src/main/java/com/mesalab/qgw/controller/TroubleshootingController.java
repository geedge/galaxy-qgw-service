package com.mesalab.qgw.controller;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.http.HttpStatus;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.enums.*;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.qgw.exception.QGWBusinessException;
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
    public JSONObject ComponentStatus() {
        return troubleshootingService.getComponentStatus();
    }

    @RequestMapping(value = "sanity", method = RequestMethod.GET)
    public BaseResult sanity(String test) {
        if (Arrays.stream(ConsistencyOptionEnum.values()).noneMatch(o -> o.getValue().equalsIgnoreCase(test))) {
            throw new QGWBusinessException(HttpStatus.HTTP_BAD_REQUEST, ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), QGWMessageConst.CONSISTENCY_OPTION_ERROR));
        }
        if (ConsistencyOptionEnum.TTL_CONSISTENCY.getValue().equalsIgnoreCase(test)) {
            log.info("ttl consistency check interface.");
            return troubleshootingService.consistencyCheck();
        } else {
            log.info("schema consistency check interface.");
            return troubleshootingService.validateMetadata();
        }
    }

    @RequestMapping(value = "/benchmark", method = RequestMethod.GET)
    public BaseResult benchmark(@RequestParam(defaultValue = "validation") String test, @RequestParam(value = "is_saved", required = false, defaultValue = "0") @JsonProperty("isSaved") Integer isSaved, @RequestParam(required = false) String category) throws BusinessException {
        if (Arrays.stream(BenchmarkOptionEnum.values()).noneMatch(o -> o.getValue().equalsIgnoreCase(test))) {
            throw new QGWBusinessException(HttpStatus.HTTP_BAD_REQUEST, ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), QGWMessageConst.BENCHMARK_OPTION_ERROR));
        }
        return troubleshootingService.benchmarkTest(test, BooleanUtil.toBoolean(String.valueOf(isSaved)), category);
    }

}
