package com.mesalab.qgw.service;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.enums.DiagnosisOptionEnum;
import org.apache.http.HttpStatus;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

import static org.junit.Assert.assertEquals;

@EnableAutoConfiguration
public class EngineTest extends GalaxyQGWApplicationTests {
    private static final Log log = LogFactory.get();
    @Autowired
    private DiagnosisService diagnosisService;

    @Test
    public void testSchema() {
        BaseResult baseResult = diagnosisService.validateSchema();
        assertEquals("Schema validate failure.", String.valueOf(baseResult.getStatus()), String.valueOf(HttpStatus.SC_OK));
    }

    @Test
    public void testMetadata() {
        BaseResult baseResult = diagnosisService.validateMetadata();
        assertEquals("Metadata validate failure.", String.valueOf(baseResult.getStatus()), String.valueOf(HttpStatus.SC_OK));
    }

    @Test
    public void testPocSql() {
        BaseResult baseResult = diagnosisService.runPocSQL(false, DiagnosisOptionEnum.VALIDATION.getValue());
        assertEquals("POC SQL validate failure.", String.valueOf(baseResult.getStatus()), String.valueOf(HttpStatus.SC_OK));
    }

}
