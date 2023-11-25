package com.mesalab.qgw.controller;

import cn.hutool.http.HttpStatus;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.enums.DBTypeEnum;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.common.enums.DiagnosisOptionEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.qgw.exception.QGWBusinessException;
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

    @RequestMapping(value = "ttl_consistency_check", method = RequestMethod.POST)
    public BaseResult monitorCluster() {
        log.info("consistency check interface.");
        return diagnosisService.consistencyCheck();
    }

    @RequestMapping(value = "/schema_check")
    public BaseResult validateSchema() {
        return diagnosisService.validateSchema();
    }

    @RequestMapping(value = "/metadata_check")
    public BaseResult validateMetadata() {
        return diagnosisService.validateMetadata();
    }

    @RequestMapping(value = "/sql_benchmark")
    public BaseResult executePocSql(@RequestParam(value = "is_saved",required = false) @JsonProperty("isSaved") String isSaved, @RequestParam(defaultValue = "validation") String option, @RequestParam(required = false) String category) throws BusinessException {
        if (Arrays.stream(DiagnosisOptionEnum.values()).noneMatch(o -> o.getValue().equalsIgnoreCase(option))) {
            throw new QGWBusinessException(HttpStatus.HTTP_BAD_REQUEST, ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), QGWMessageConst.DIAGNOSIS_OPTION_ERROR));
        }
        return diagnosisService.runPocSQL(Boolean.parseBoolean(isSaved), option, category);
    }

    @RequestMapping({"/sql", "/sql/{dialect}/{queryNo}"})
    public BaseResult getPocSql(@PathVariable  String dialect, @PathVariable(required = false) Integer queryNo) {
        if (Arrays.stream(DBTypeEnum.values()).noneMatch(o -> o.getValue().equalsIgnoreCase(dialect))){
            throw new QGWBusinessException(HttpStatus.HTTP_BAD_REQUEST, ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), QGWMessageConst.DIALECT_VALUE_ERROR));
        }
        return diagnosisService.getPocSQL(dialect, queryNo);
    }

    @RequestMapping(value = "/entity")
    public BaseResult getMessageInfo(String option) {
        log.info("Entity recommendation proportion statistical information interface, param is: {}", option);
        if (!EntityQueryType.TOPSERVERIP.getType().equalsIgnoreCase(option) && !EntityQueryType.TOPSNI.getType().equalsIgnoreCase(option)) {
            throw new QGWBusinessException(HttpStatus.HTTP_BAD_REQUEST, ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), QGWMessageConst.DIAGNOSIS_OPTION_ERROR));
        }
        return diagnosisService.getMessageInfo(option);
    }
}
