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
 * @data 2019/7/10 18:24
 */
@Slf4j
@EnableAutoConfiguration
public class MysqlCKDateTest extends GalaxyQGWApplicationTests {

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

    /**
     * 安全策略接口
     */
    @Test
    public void securityEventLog(){
        String sql = "select FROM_UNIXTIME(common_recv_time) as common_recv_time,common_subscriber_id, common_address_type, common_l4_protocol, common_client_ip, common_server_ip, common_server_port, ssl_sni, ssl_version from security_event_log where common_recv_time >= UNIX_TIMESTAMP($start_time) and common_recv_time< UNIX_TIMESTAMP($end_time) and common_client_ip like '49.7%' order by common_recv_time desc limit 0,20";
        sql = sql.replaceAll("\\$start_time", "'2019-11-10 00:00:00'");
        sql = sql.replaceAll("\\$end_time", "'2019-12-10 00:00:00'");
        url = url + "/?query=" + sql ;
        Map map = restTemplate.getForObject(url, Map.class);
        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }

    /**
     * 代理策略接口
     */
    @Test
    public void proxyEventLog(){
        String sql = "select FROM_UNIXTIME(common_recv_time) as common_recv_time,common_subscriber_id, common_address_type, common_l4_protocol, common_client_ip, common_server_ip, common_server_port, http_host,http_url,http_user_agent from proxy_event_log where common_recv_time >= UNIX_TIMESTAMP($start_time) and common_recv_time< UNIX_TIMESTAMP($end_time) and http_host like '%joy.cn%' order by common_recv_time desc limit 0,20";
        sql = sql.replaceAll("\\$start_time", "'2019-11-10 00:00:00'");
        sql = sql.replaceAll("\\$end_time", "'2019-12-10 00:00:00'");
        url = url + "/?query=" + sql ;
        Map map = restTemplate.getForObject(url, Map.class);
        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }

    /**
     * 协议采集日志
     */
    @Test
    public void connectionRecordLog(){
        String sql = "select FROM_UNIXTIME(common_recv_time) as common_recv_time,common_subscriber_id, common_address_type, common_l4_protocol, common_client_ip, common_server_ip, common_server_port, http_host,http_url,http_user_agent from proxy_event_log where common_recv_time >= UNIX_TIMESTAMP($start_time) and common_recv_time< UNIX_TIMESTAMP($end_time) and http_host like '%joy.cn%' order by common_recv_time desc limit 0,20";
        sql = sql.replaceAll("\\$start_time", "'2019-11-10 00:00:00'");
        sql = sql.replaceAll("\\$end_time", "'2019-12-10 00:00:00'");
        url = url + "/?query=" + sql ;
        Map map = restTemplate.getForObject(url, Map.class);
        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }

    /**
     * RADIUS采集日志
     */
    @Test
    public void radiusRecordLog(){
        String sql = "select FROM_UNIXTIME(common_recv_time) as common_recv_time, common_address_type, common_l4_protocol, common_client_ip, common_server_ip, common_server_port, radius_framed_ip, radius_account from radius_record_log where common_recv_time >= UNIX_TIMESTAMP($start_time) and common_recv_time< UNIX_TIMESTAMP($end_time) and radius_account='T1yRd' order by common_recv_time desc limit 0,20";
        sql = sql.replaceAll("\\$start_time", "'2019-11-10 00:00:00'");
        sql = sql.replaceAll("\\$end_time", "'2019-12-10 00:00:00'");
        url = url + "/?query=" + sql ;
        Map map = restTemplate.getForObject(url, Map.class);
        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }


}
