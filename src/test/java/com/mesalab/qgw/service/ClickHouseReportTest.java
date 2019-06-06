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

@Slf4j
@EnableAutoConfiguration
public class ClickHouseReportTest extends GalaxyQGWApplicationTests {

    @Autowired
    public TestRestTemplate restTemplate;
    private String url = "http://127.0.0.1:9000";


    @Before
    public void testBefore() {
        log.info("=================================================CLICKHOUSE REPORT TEST BEGIN=================================================");
    }

    @Test
    public void testTop100SourceBySessions() {
        String sql = "select client_ip,count(*) as sessions from session_record_log where recv_time>= toDateTime('2019-05-27 00:00:00') and recv_time< toDateTime('2019-06-03 00:00:00') group by client_ip order by sessions desc limit 0,100";
        url = url + "/?query=" + sql ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }


    @Test
    public void testTop100DestinationsBySessions() {
        String sql = "select server_ip,count(*) as sessions from session_record_log where recv_time>= toDateTime('2019-05-27 00:00:00') and recv_time< toDateTime('2019-06-03 00:00:00') group by server_ip order by sessions desc limit 0,100";
        url = url + "/?query=" + sql ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }

    @Test
    public void testTop100WebsiteDomainsByBandwidth() {
        String sql = "SELECT domain AS domain,SUM(coalesce(c2s_byte_num, 0)) AS sent_bytes,SUM(coalesce(s2c_byte_num, 0)) AS received_bytes,SUM(plus(coalesce(c2s_byte_num, 0),coalesce(s2c_byte_num, 0))) AS bytes FROM session_record_log WHERE recv_time >= toDateTime('2019-05-31 00:00:00') AND recv_time <toDateTime('2019-06-01 00:00:00') and notEmpty(domain) GROUP BY domain ORDER BY bytes DESC LIMIT 100";
        url = url + "/?query=" + sql ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }



    @After
    public void testAfter() {
        log.info("=================================================CLICKHOUSE REPORT TEST  END=================================================");

    }



}
