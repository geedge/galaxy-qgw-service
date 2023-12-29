package com.mesalab.qgw.controller;

import cn.hutool.http.HttpStatus;
import com.alibaba.fastjson.JSONObject;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.enums.DBType;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.qgw.service.DiagnosisService;
import com.mesalab.services.common.enums.EntityQueryType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

/**
 * Galaxy日志自动诊断接口
 */
@Slf4j
@RestController
@RequestMapping(value = "/v1/monitor")
public class DiagnosisController {

    @Autowired
    private DiagnosisService diagnosisService;

    @RequestMapping("/component_info")
    public JSONObject versionInfo() {
        return diagnosisService.getVersionInfo();
    }


    @RequestMapping(value = "/schema_check")
    public BaseResult validateSchema() {
        return diagnosisService.validateSchema();
    }

    @RequestMapping({"/sql", "/sql/{dialect}/{queryNo}"})
    public BaseResult getPocSql(@PathVariable  String dialect, @PathVariable(required = false) Integer queryNo) {
        if (Arrays.stream(DBType.values()).noneMatch(o -> o.getValue().equalsIgnoreCase(dialect))){
            throw new QGWBusinessException(HttpStatus.HTTP_BAD_REQUEST, CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.DIALECT_VALUE_ERROR));
        }
        return diagnosisService.getPocSQL(dialect, queryNo);
    }

    @RequestMapping(value = "/entity")
    public BaseResult getMessageInfo(String option) {
        log.info("Entity recommendation proportion statistical information interface, param is: {}", option);
        if (!EntityQueryType.TOPSERVERIP.getType().equalsIgnoreCase(option) && !EntityQueryType.TOPSNI.getType().equalsIgnoreCase(option)) {
            throw new QGWBusinessException(HttpStatus.HTTP_BAD_REQUEST, CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.DIAGNOSIS_OPTION_ERROR));
        }
        return diagnosisService.getMessageInfo(option);
    }
}
