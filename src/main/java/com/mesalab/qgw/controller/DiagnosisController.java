package com.mesalab.qgw.controller;

import com.mesalab.common.base.BaseResult;
import com.mesalab.qgw.service.impl.DiagnosisServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Galaxy日志自动诊断接口
 */
@Slf4j
@RestController
@RequestMapping(value = "/diagnosis")
public class DiagnosisController {
    @Autowired
   private DiagnosisServiceImpl diagnosisService;

    @RequestMapping(value = "/schema")
    public BaseResult validateSchema() {
        return diagnosisService.validateSchema();
    }

    @RequestMapping(value = "/metadata")
    public BaseResult validateMetadata() {
        return diagnosisService.validateMetadata();
    }

}
