package com.mesalab.qgw.service;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.zdjizhi.utils.JsonMapper;
import com.zdjizhi.utils.StringUtil;
import org.apache.avro.JsonProperties;
import org.apache.avro.Schema;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

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
        assertEquals("Schema格式错误！", String.valueOf(baseResult.getStatus()), String.valueOf(HttpStatus.SC_OK));
    }

}
