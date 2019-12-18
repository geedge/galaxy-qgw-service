package com.mesalab;

import com.mesalab.common.utils.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Map;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {GalaxyQGWApplication.class}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Slf4j
@EnableAutoConfiguration
public class GalaxyQGWApplicationTests {
    @Autowired
    public TestRestTemplate restTemplate;

    @Before
    public void testBefore() {
        log.info("=================================================GALAXY QUERY GATEWAY BEGIN=================================================");
    }

    @Test
    public void testHttpGetMethod() {

        String url = "http://192.168.40.186:9999";
        Map map = restTemplate.getForObject(url, Map.class);
        log.info(JsonMapper.toJsonString(map));
    }

    @Test
    public void contextLoads() {
        System.out.println("test");
    }

    @After
    public void testAfter() {
        log.info("=================================================GALAXY QUERY GATEWAY END=================================================");

    }

}
