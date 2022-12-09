package com.mesalab.qgw.service;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.entity.BaseResult;
import org.apache.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

import static org.junit.Assert.assertEquals;

@EnableAutoConfiguration
public class AvroSchemaFormatTest extends GalaxyQGWApplicationTests {
    private static final Log log = LogFactory.get();
    @Autowired
    public DiagnosisService diagnosisService;
    @Before
    public void testBefore() {
        log.info("=================================================SCHEMA TEST BEGIN=================================================");
    }

    @After
    public void testAfter() {
        log.info("=================================================SCHEMA TEST END=================================================");
    }

    /**
     * 测试schema
     */
    @Test
    public void testCheckSchema() {
        BaseResult baseResult = diagnosisService.validateSchema();
        assertEquals("Schema format failure.", String.valueOf(baseResult.getStatus()), String.valueOf(HttpStatus.SC_OK));
    }

}
