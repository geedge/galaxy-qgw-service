package com.mesalab.qgw.controller;

import cn.hutool.http.HttpStatus;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.common.enums.DiagnosisOptionEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.qgw.exception.QGWErrorCode;
import com.mesalab.qgw.service.DiagnosisService;
import com.mesalab.services.common.enums.EntityQueryType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

/**
 * Galaxy日志自动诊断接口
 */
@Slf4j
@RestController
@RequestMapping(value = "/diagnosis")
public class DiagnosisController {

    @Autowired
    private DiagnosisService diagnosisService;

    @RequestMapping(value = "/schema")
    public BaseResult validateSchema() {
        return diagnosisService.validateSchema();
    }

    @RequestMapping(value = "/metadata")
    public BaseResult validateMetadata() {
        return diagnosisService.validateMetadata();
    }

    @RequestMapping(value = "/runSql")
    public BaseResult executePocSql(String isSaved, @RequestParam(defaultValue = "parse") String option) throws BusinessException {
        if (Arrays.stream(DiagnosisOptionEnum.values()).noneMatch(o -> o.getValue().equalsIgnoreCase(option))) {
            throw new QGWBusinessException(HttpStatus.HTTP_BAD_REQUEST, QGWErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(QGWErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.DIAGNOSIS_OPTION_ERROR));
        }
        return diagnosisService.runPocSQL(Boolean.parseBoolean(isSaved), option);
    }

    @RequestMapping({"/sql", "/sql/{dialect}/{queryNo}"})
    public BaseResult getPocSql(@PathVariable String dialect, @PathVariable(required = false) Integer queryNo) {
        return diagnosisService.getPocSQL(dialect, queryNo);
    }

    @RequestMapping(value = "/entity")
    public BaseResult getMessageInfo(String option) {
        log.info("Entity recommendation proportion statistical information interface, param is: {}", option);
        if (!EntityQueryType.TOPSERVERIP.getType().equalsIgnoreCase(option) && !EntityQueryType.TOPSNI.getType().equalsIgnoreCase(option)) {
            throw new QGWBusinessException(HttpStatus.HTTP_BAD_REQUEST, QGWErrorCode.PARAMETER_ERROR.getCode(),
            String.format(QGWErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.DIAGNOSIS_OPTION_ERROR));
        }
        return diagnosisService.getMessageInfo(option);
    }
}
