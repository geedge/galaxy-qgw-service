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

import java.util.*;

@Slf4j
@EnableAutoConfiguration
public class GetSchemaInfo extends GalaxyQGWApplicationTests {


    @Autowired
    public TestRestTemplate restTemplate;

    public static List<String> dataSource = Arrays.asList("tsg_galaxy_v3");
    public static List<String> tables = Arrays.asList("connection_record_log", "radius_record_log", "proxy_event_log", "security_event_log");


    private String url = "http://127.0.0.1:9999";

    /**
     * show语句 查询库表
     */
    @Test
    public void showSchema(){
        String sql = "show tables";
        url = url + "/?query=" + sql ;
        Map map = restTemplate.getForObject(url, Map.class);
        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }

    /**
     * desc 查询表信息
     */
    @Test
    public void descTable(){
        String sql = "describe connection_record_log";
        url = url + "/?query=" + sql ;
        Map map = restTemplate.getForObject(url, Map.class);
        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }

    /**
     * 接口访问库表
     */
    @Test
    public void interfaceGetSchema(){
        url = url + "/metadata/schema/v1/tables/";
        Iterator<String> iterator = dataSource.iterator();
        while (iterator.hasNext()){
            String next = iterator.next();
            Map map = restTemplate.getForObject(url+next, Map.class);
            log.info(JsonMapper.toJsonString(map));
            Assert.assertEquals((int)map.get("status"), 200);
        }
    }

    /**
     * 接口访问库表
     */
    @Test
    public void interfaceGetTable(){
        url = url + "/metadata/schema/v1/fields/" ;
        Iterator<String> iterator = tables.iterator();
        while (iterator.hasNext()){
            String next = iterator.next();
            Map map = restTemplate.getForObject(url+next, Map.class);
            log.info(JsonMapper.toJsonString(map));
            Assert.assertEquals((int)map.get("status"), 200);
        }

    }


    @Before
    public void testBefore() {
        log.info("=================================================QGW GET SCHEMA INFO TEST BEGIN=================================================");
    }

    @After
    public void testAfter() {
        log.info("=================================================QGW GET SCHEMA INFO TEST  END=================================================");
    }

}
