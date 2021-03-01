package com.mesalab.qgw.controller;

import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.service.DiagnosisService;
import com.mesalab.qgw.service.impl.DiagnosisServiceImpl;
import com.zdjizhi.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    public BaseResult executePocSql(String isSaved) {
        if (StringUtil.isNotBlank(isSaved)) {
            return diagnosisService.runPocSQL(Boolean.valueOf(isSaved));
        } else {
            return diagnosisService.runPocSQL(false);
        }


    }

    @RequestMapping({"/sql", "/sql/{dialect}/{queryNo}"})
    public BaseResult getPocSql(@PathVariable String dialect, @PathVariable(required = false) Integer queryNo) {
        return diagnosisService.getPocSQL(dialect, queryNo);
    }

}
