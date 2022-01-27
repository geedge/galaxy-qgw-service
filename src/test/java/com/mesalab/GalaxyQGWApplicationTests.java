package com.mesalab;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {GalaxyQGWApplication.class}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableAutoConfiguration
public class GalaxyQGWApplicationTests {
    private static final Log log = LogFactory.get();

    static {
        System.setProperty("jasypt.encryptor.password", "galaxy");
    }

    @Before
    public void testBefore() {
        log.info("=================================================GALAXY QUERY GATEWAY BEGIN=================================================");

    }

    @Test
    public void contextLoads() {
        log.info("GALAXY QUERY GATEWAY CONTEXT LOAD");
    }

    @After
    public void testAfter() {
        log.info("=================================================GALAXY QUERY GATEWAY END=================================================");

    }

}
