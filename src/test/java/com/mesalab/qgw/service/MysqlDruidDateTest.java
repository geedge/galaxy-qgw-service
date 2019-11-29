package com.mesalab.qgw.service;

import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.utils.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import java.util.Map;

/**
 * @author GouGe
 * @data 2019/8/14 14:29
 */

@Slf4j
@EnableAutoConfiguration
public class MysqlDruidDateTest extends GalaxyQGWApplicationTests {


    @Autowired
    public TestRestTemplate restTemplate;

    private String url = "http://127.0.0.1:9999";

    @Before
    public void testBefore() {
        log.info("=================================================QGW TIME FUNCTION TEST BEGIN=================================================");
    }

    @After
    public void testAfter() {
        log.info("=================================================QGW TIME FUNCTION TEST  END=================================================");
    }
}
