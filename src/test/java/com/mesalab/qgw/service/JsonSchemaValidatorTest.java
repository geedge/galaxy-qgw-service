package com.mesalab.qgw.service;


import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.entity.BaseResult;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

import java.util.Map;


@EnableAutoConfiguration
public class JsonSchemaValidatorTest extends GalaxyQGWApplicationTests {
    private static final Log log = LogFactory.get();

    @Autowired
    private TroubleshootingService troubleshootingService;

    @Test
    public void testJsonSchemaValidator() {
        BaseResult result = troubleshootingService.validateMetadata();
        Map resultData = (Map) result.getData();
        if (!resultData.isEmpty()){
           log.warn(resultData.toString());
        }
        Assert.assertTrue(result.getMessage(), result.getStatus() == 200);
    }
}
