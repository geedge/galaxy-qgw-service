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
public class DruidTest extends GalaxyQGWApplicationTests {
    @Autowired
    public TestRestTemplate restTemplate;
    private String url = "http://127.0.0.1:9000";


    @Before
    public void testBefore() {
        log.info("=================================================DRUID TEST BEGIN=================================================");
    }

    @Test
    public void testBandwidthTrend() {
        String sql = "select TIME_FORMAT(__time,'yyyy-MM-dd HH:mm:ss') as stat_time, direction,sum(c2s_byte_num+s2c_byte_num)*8/30 as bps from bandwidth_log where __time >= TIMESTAMP '2019-05-29 03:00:00' and  __time < TIMESTAMP '2019-05-30 04:00:00' group by __time,direction order by stat_time asc";
        url = url + "/?query=" + sql ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }

    @Test
    public void testTrafficMetrics() {
        String sql = "select  sum(c2s_byte_num+s2c_byte_num)*8/300 as num, 'traffic_in' as type from bandwidth_log where  __time >= TIME_SHIFT(TIME_FLOOR(CURRENT_TIMESTAMP,'PT5M'),'PT10M',-1) and __time < TIME_SHIFT(TIME_FLOOR(CURRENT_TIMESTAMP,'PT5M'),'PT5M',-1)  and direction=1 union all select  sum(c2s_byte_num+s2c_byte_num)*8/300 as num, 'traffic_out' as type from bandwidth_log where  __time >= TIME_SHIFT(TIME_FLOOR(CURRENT_TIMESTAMP,'PT5M'),'PT10M',-1) and __time < TIME_SHIFT(TIME_FLOOR(CURRENT_TIMESTAMP,'PT5M'),'PT5M',-1) and direction=0 union all select COALESCE(sum(close_conn_num),0)/300 as num,'close_conn_num' as type  from traffic_metrics_log where   __time >= TIME_SHIFT(TIME_FLOOR(CURRENT_TIMESTAMP,'PT5M'),'PT10M',-1) and __time < TIME_SHIFT(TIME_FLOOR(CURRENT_TIMESTAMP,'PT5M'),'PT5M',-1) union all select  COALESCE(sum(live_conn_num),0) /300 as num,'live_conn_num' as type from traffic_metrics_log  where   __time >= TIME_SHIFT(TIME_FLOOR(CURRENT_TIMESTAMP,'PT5M'),'PT10M',-1) and __time < TIME_SHIFT(TIME_FLOOR(CURRENT_TIMESTAMP,'PT5M'),'PT5M',-1) union all select    COALESCE(sum(new_conn_num),0) /300 as num,'new_conn_num' as type from traffic_metrics_log  where   __time >= TIME_SHIFT(TIME_FLOOR(CURRENT_TIMESTAMP,'PT5M'),'PT10M',-1) and __time < TIME_SHIFT(TIME_FLOOR(CURRENT_TIMESTAMP,'PT5M'),'PT5M',-1) union all select    COALESCE(sum(hits),0) /300 as num,'drop_num' as type from policy_hits_log where  __time=TIME_SHIFT(TIME_FLOOR(CURRENT_TIMESTAMP,'PT5M'),'PT5M',-1) and action=16 " +
                "union all select    COALESCE(sum(hits),0) /300 as num,'manipulate_num' as type from policy_hits_log where  __time=TIME_SHIFT(TIME_FLOOR(CURRENT_TIMESTAMP,'PT5M'),'PT5M',-1) and action=48 " +
                "union all select    COALESCE(sum(hits),0) /300 as num,'bypass_num' as type from policy_hits_log where  __time=TIME_SHIFT(TIME_FLOOR(CURRENT_TIMESTAMP,'PT5M'),'PT5M',-1) and action=128 ";
        url = url + "/?query=" + sql ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }

    @Test
    public void testTopPolicyHits() {
        String sql = "select policy_id,sum(hits) as hits,sum(c2s_byte_num) as sent_bytes, sum(s2c_byte_num) as received_bytes, sum(c2s_byte_num+s2c_byte_num) as bytes,sum(c2s_pkt_num) as sent_packets ,sum(s2c_pkt_num) as received_packets, sum(c2s_pkt_num+s2c_pkt_num) as packets from policy_hits_log where __time >= TIMESTAMP '2019-05-29 03:00:00' and  __time < TIMESTAMP '2019-05-30 04:00:00'   group by policy_id order by hits desc limit 100";
        url = url + "/?query=" + sql ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }

    @Test
    public void testTopInternalIpBySessions() {
        String sql = "select source,sum(session_num) as sessions, sum(c2s_byte_num) as sent_bytes, sum(s2c_byte_num) as received_bytes,  sum(c2s_pkt_num) as sent_packets ,sum(s2c_pkt_num) as received_packets from top_internal_host_log where __time=TIME_SHIFT(TIME_FLOOR(CURRENT_TIMESTAMP,'PT5M'),'PT10M',-1) group by source order by sessions desc limit 1,100";
        url = url + "/?query=" + sql ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }
    @Test
    public void testTopExternalIpBySessions() {
        String sql = "select destination,protocol,sum(session_num) as sessions, sum(c2s_byte_num) as sent_bytes, sum(s2c_byte_num) as received_bytes,  sum(c2s_pkt_num) as sent_packets ,sum(s2c_pkt_num) as received_packets from top_external_host_log where __time=TIME_SHIFT(TIME_FLOOR(CURRENT_TIMESTAMP,'PT5M'),'PT10M',-1) group by destination,protocol order by sessions desc limit 0,100";
        url = url + "/?query=" + sql ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }

    @Test
    public void testTopWebsiteDomainByBandwidth() {
        String sql = "select domain,sum(session_num) as sessions, sum(c2s_byte_num) as sent_bytes, sum(c2s_byte_num+s2c_byte_num) as bytes,  sum(s2c_byte_num) as received_bytes,  sum(c2s_pkt_num) as sent_packets ,sum(s2c_pkt_num) as received_packets,sum(c2s_pkt_num+s2c_pkt_num) as packets from top_website_domain_log where __time=TIME_SHIFT(TIME_FLOOR(CURRENT_TIMESTAMP,'PT5M'),'PT10M',-1) group by domain order by bytes desc limit 100";
        url = url + "/?query=" + sql ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }


    public void testTopUrlsBySessions() {
        String sql = "select url,sum(session_num) as sessions, sum(c2s_byte_num) as sent_bytes, sum(c2s_byte_num+s2c_byte_num) as bytes,  sum(s2c_byte_num) as received_bytes,  sum(c2s_pkt_num) as sent_packets ,sum(s2c_pkt_num) as received_packets,sum(c2s_pkt_num+s2c_pkt_num) as packets from top_urls_log where __time=TIME_SHIFT(TIME_FLOOR(CURRENT_TIMESTAMP,'PT5M'),'PT10M',-1) group by url order by sessions desc limit 100";
        url = url + "/?query=" + sql ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }

    public void testTopUsersByBandwidth() {
        String sql = "select  subscribe_id, sum(c2s_byte_num) as sent_bytes, sum(s2c_byte_num) as received_bytes , sum(c2s_byte_num+s2c_byte_num) as bytes  from top_user_log where __time=TIME_SHIFT(TIME_FLOOR(CURRENT_TIMESTAMP,'PT5M'),'PT10M',-1) group by subscribe_id order by bytes desc limit 10";
        url = url + "/?query=" + sql ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }



    @After
    public void testAfter() {
        log.info("=================================================DRUID TEST  END=================================================");

    }





}
