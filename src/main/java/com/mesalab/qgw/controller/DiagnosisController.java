package com.mesalab.qgw.controller;

import cn.hutool.http.HttpStatus;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.constant.MessageConst;
import com.mesalab.common.enums.DiagnosisOptionEnum;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.qgw.service.DiagnosisService;
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
            throw new BusinessException(HttpStatus.HTTP_BAD_REQUEST, ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                    MessageConst.DIAGNOSIS_OPTION_ERROR);
        }
        return diagnosisService.runPocSQL(Boolean.parseBoolean(isSaved), option);
    }

    @RequestMapping({"/sql", "/sql/{dialect}/{queryNo}"})
    public BaseResult getPocSql(@PathVariable String dialect, @PathVariable(required = false) Integer queryNo) {
        return diagnosisService.getPocSQL(dialect, queryNo);
    }

}
