package com.mesalab.qgw.controller;

import cn.hutool.http.HttpStatus;
import com.alibaba.fastjson2.JSONObject;
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

    @RequestMapping(value = "/component_info", method = RequestMethod.GET)
    @AuditLog("DiagnosisController.versionInfo")
    public JSONObject versionInfo() {
        return diagnosisService.getVersionInfo();
    }


    @RequestMapping(value = "/schema_check", method = RequestMethod.POST)
    @AuditLog("DiagnosisController.validateSchema")
    public BaseResult validateSchema() {
        return diagnosisService.validateSchema();
    }

    @RequestMapping(value = {"/sql", "/sql/{dialect}/{queryNo}"}, method = RequestMethod.GET)
    @AuditLog("DiagnosisController.getPocSql")
    public BaseResult getPocSql(@PathVariable String dialect, @PathVariable(required = false) Integer queryNo) {
        if (Arrays.stream(DBEngineType.values()).noneMatch(o -> o.getValue().equalsIgnoreCase(dialect))) {
            throw new QGWBusinessException(HttpStatus.HTTP_BAD_REQUEST, CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.DIALECT_VALUE_ERROR));
        }
        return diagnosisService.getPocSQL(dialect, queryNo);
    }

}
