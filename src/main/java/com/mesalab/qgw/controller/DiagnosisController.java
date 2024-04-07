package com.mesalab.qgw.controller;

import cn.hutool.http.HttpStatus;
import com.alibaba.fastjson.JSONObject;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.enums.DBEngineType;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.qgw.model.basic.AuditLog;
import com.mesalab.qgw.service.DiagnosisService;
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
    @AuditLog("DiagnosisController.versionInfo")
    public JSONObject versionInfo() {
        return diagnosisService.getVersionInfo();
    }


    @RequestMapping(value = "/schema_check")
    @AuditLog("DiagnosisController.validateSchema")
    public BaseResult validateSchema() {
        return diagnosisService.validateSchema();
    }

    @RequestMapping({"/sql", "/sql/{dialect}/{queryNo}"})
    @AuditLog("DiagnosisController.getPocSql")
    public BaseResult getPocSql(@PathVariable  String dialect, @PathVariable(required = false) Integer queryNo) {
        if (Arrays.stream(DBEngineType.values()).noneMatch(o -> o.getValue().equalsIgnoreCase(dialect))){
            throw new QGWBusinessException(HttpStatus.HTTP_BAD_REQUEST, CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.DIALECT_VALUE_ERROR));
        }
        return diagnosisService.getPocSQL(dialect, queryNo);
    }

}
