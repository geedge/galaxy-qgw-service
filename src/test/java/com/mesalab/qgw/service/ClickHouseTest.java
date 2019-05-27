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
public class ClickHouseTest extends GalaxyQGWApplicationTests {
    @Autowired
    public TestRestTemplate restTemplate;
    private String url = "http://127.0.0.1:9000";

    @Before
    public void testBefore() {
        log.info("=================================================CLICKHOUSE TEST BEGIN=================================================");
    }

    @Test
    public void testHttpGetMethod() {
        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));

        Assert.assertEquals((int)map.get("status"), 200);
    }

    @Test
    public void testSQLFromSessionRecordLogs() {
        String sql = "select recv_time,subscribe_id, trans_proto, client_ip, server_ip, server_port,host,url,con_duration_ms,entrance_id,c2s_pkt_num, s2c_pkt_num, c2s_byte_num, s2c_byte_num FROM session_record_log where recv_time=(select max(recv_time) from session_record_log) limit 10,30";
        url = url + "/?query=" + sql ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }

    @Test
    public void testSQLFromPolicyEventLogs() {
        String sql = "select recv_time,policy_id,subscribe_id, trans_proto, client_ip, server_ip, server_port,host,url,con_duration_ms,entrance_id,action,c2s_pkt_num, s2c_pkt_num, c2s_byte_num, s2c_byte_num FROM policy_event_log where recv_time>= toDateTime('2019-02-24 05:55:00') and recv_time< toDateTime('2019-02-24 06:00:00') and entrance_id=1 and client_ip like '83.200%' and server_ip='178.89.12.222' order by recv_time desc limit 0,20";
        url = url + "/?query=" + sql ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }

    @Test
    public void testSQLFromRadiusLogs() {
        String sql = "select recv_time,account,nas_ip,framed_ip,packet_type, trans_proto, client_ip, server_ip, server_port FROM radius_log where recv_time>= toDateTime('2019-02-24 05:55:00') and recv_time<toDateTime('2019-02-24 06:00:00') and nas_ip='178.89.12.222' order by recv_time desc limit 0,20";
        url = url + "/?query=" + sql ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map) +  map.get("status"));

        Assert.assertEquals((int)map.get("status"), 200);

    }


    @After
    public void testAfter() {
        log.info("=================================================CLICKHOUSE TEST  END=================================================");

    }

}
