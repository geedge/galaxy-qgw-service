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


    /**
     * 1小时带宽变化示例（粒度为30秒）
     */
    @Test
    public void BandwithOf1Hour(){
        String sql = "select $format as stat_time, 0 as direction,sum(c2s_byte_num)*8/30 as bps from bandwidth_log where $filter group by __time union all select $format as stat_time, 1 as direction,sum(s2c_byte_num)*8/30 as bps from bandwidth_log where $filter group by __time ";

        sql = sql.replaceAll("\\$filter", "__time >=  '2019-08-13 03:00:00' and __time < '2019-08-13 04:00:00'");
        sql = sql.replaceAll("\\$format", "DATE_FORMAT(__time,'%Y-%m-%d %H:%i:%s' )");
        //sql = sql.replaceAll("\\$filter", "__time >= TIMESTAMP '2019-08-13 03:00:00' and __time < TIMESTAMP '2019-08-13 04:00:00'");
        //sql = sql.replaceAll("\\$format", "TIME_FORMAT(__time,'yyyy-MM-dd HH:mm:ss')");

        url = url + "/?query=" + sql;
        Map map = restTemplate.getForObject(url, Map.class);
        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }

    /**
     * 1周带宽变化示例（粒度为5分钟）
     */
    @Test
    public void BandwithOf1Week(){
        String sql = "select $format as stat_time, 0 as direction,sum(c2s_byte_num)*8/300 as bps from bandwidth_log where $filter group by $format union all select $format as stat_time, 1 as direction,sum(s2c_byte_num)*8/300 as bps from bandwidth_log where $filter group by $format";

        sql = sql.replaceAll("\\$filter", "__time >= ADDDATE(DATE(now()),-DAYOFWEEK(NOW())+1) and __time < ADDDATE(DATE(now()),-DAYOFWEEK(now())+8)");
        sql = sql.replaceAll("\\$format", "FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/300)*300)");
        //sql = sql.replaceAll("\\$filter", "__time >= TIMESTAMP '2019-08-11 00:00:00' and __time < TIMESTAMP '2019-08-18 00:00:00'");
        //sql = sql.replaceAll("\\$format", "TIME_FORMAT(time_floor(__time,'PT5M'),'yyyy-MM-dd HH:mm:ss')");

        url = url + "/?query=" + sql ;
        Map map = restTemplate.getForObject(url, Map.class);
        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }

    /**
     * 一月带宽变化示例（粒度为1小时）
     */
    @Test
    public void BandwithOf1Month(){
        String sql = "select $time_granularity as stat_time, 0 as direction,sum(c2s_byte_num)*8/3600 as bps from bandwidth_log where $filter group by $time_granularity union all select $time_granularity as stat_time, 1 as direction,sum(s2c_byte_num)*8/3600 as bps from bandwidth_log where $filter group by $time_granularity";

        sql = sql.replaceAll("\\$filter", "__time >=  DATE_SUB('2019-08-13 00:00:00',interval 1 day) and __time < '2019-08-13 00:00:00'");
        sql = sql.replaceAll("\\$time_granularity", "FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/3600)*3600)");
        //sql = sql.replaceAll("\\$filter", "__time >= TIMESTAMP '2019-08-12 00:00:00' and __time < TIMESTAMP '2019-08-13 00:00:00'");
        //sql = sql.replaceAll("\\$time_granularity", "TIME_FORMAT(time_floor(__time, 'PT1H'),'yyyy-MM-dd HH:mm:ss')");

        url = url + "/?query=" + sql ;
        Map map = restTemplate.getForObject(url, Map.class);
        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }

    /**
     * 流量计数近5分钟
     */
    @Test
    public void trafficLately5min(){
        String sql = "select  sum(s2c_byte_num)*8/300 as num, 'traffic_in' as type from bandwidth_log where  $filter   union all select  sum(c2s_byte_num)*8/300 as num, 'traffic_out' as type from bandwidth_log where  $filter  union all select COALESCE(sum(close_conn_num),0)/300 as num,'close_conn_num' as type  from traffic_metrics_log where   $filter union all select  COALESCE(sum(live_conn_num),0) /300 as num,'live_conn_num' as type from traffic_metrics_log  where  $filter union all select    COALESCE(sum(new_conn_num),0) /300 as num,'new_conn_num' as type from traffic_metrics_log  where  $filter union all select  COALESCE(sum(hits),0) /300 as num,'drop_num' as type from policy_hits_log where  $filter2 and action=16 union all select    COALESCE(sum(hits),0) /300 as num,'manipulate_num' as type from policy_hits_log where  $filter2 and action=48 union all select    COALESCE(sum(hits),0) /300 as num,'bypass_num' as type from session_intercept_hits_log where $filter2 and action=128 union all select  COALESCE(sum(hits),0) /300 as num,'dynamic_bypass_num' as type from session_intercept_hits_log where $filter2 and intercept_state=0 union all select  COALESCE(sum(hits),0) /300 as num,'no_pinning_num' as type from session_intercept_hits_log where $filter2 and pinningst=0 union all select  COALESCE(sum(hits),0) /300 as num,'maybe_pinning_num' as type from session_intercept_hits_log where $filter2 and pinningst=2 union all select  COALESCE(sum(hits),0) /300 as num,'yes_pinning_num' as type from session_intercept_hits_log where $filter2 and pinningst=1";

        sql = sql.replaceAll("\\$filter2", "__time= adddate(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(now())/300)*300),interval -5 minute)");
        sql = sql.replaceAll("\\$filter", "__time>= adddate(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(now())/300)*300),interval -10 minute) and __time <  adddate(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(now())/300)*300),interval -5 minute)");

        //sql = sql.replaceAll("\\$filter2", "__time=TIME_SHIFT(TIME_FLOOR(CURRENT_TIMESTAMP,'PT5M'),'PT5M',-1)");
        //sql = sql.replaceAll("\\$filter", "__time >= TIME_SHIFT(TIME_FLOOR(CURRENT_TIMESTAMP,'PT5M'),'PT10M',-1) and __time < TIME_SHIFT(TIME_FLOOR(CURRENT_TIMESTAMP,'PT5M'),'PT5M',-1)");

        url = url + "/?query=" + sql ;
        Map map = restTemplate.getForObject(url, Map.class);
        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }

    /**
     * 高命中拦截策略（默认最近5分钟）
     */
    @Test
    public void InterceptPolicyOfHighHitLately5min(){
        String sql = "select policy_id,action,sum(hits) as hits,sum(c2s_byte_num) as sent_bytes, sum(s2c_byte_num) as received_bytes, sum(c2s_byte_num+s2c_byte_num) as bytes,sum(c2s_pkt_num) as sent_packets ,sum(s2c_pkt_num) as received_packets, sum(c2s_pkt_num+s2c_pkt_num) as packets from session_intercept_hits_log  where $filter group by policy_id,action order by hits desc limit 100";

        //sql = sql.replaceAll("\\$filter", "__time=date_ADD(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(now())/300)*300),interval -10 minute)");
        sql = sql.replaceAll("\\$filter", "__time=TIME_SHIFT(TIME_FLOOR(CURRENT_TIMESTAMP,'PT5M'),'PT10M',-1)");

        url = url + "/?query=" + sql ;
        Map map = restTemplate.getForObject(url, Map.class);
        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }

    /**
     * 高命中拦截策略（默认最近1小时）
     */
    @Test
    public void InterceptPolicyOfHighHitLately1Hour(){
        String sql = "select policy_id,action,sum(hits) as hits,sum(c2s_byte_num) as sent_bytes, sum(s2c_byte_num) as received_bytes, sum(c2s_byte_num+s2c_byte_num) as bytes,sum(c2s_pkt_num) as sent_packets ,sum(s2c_pkt_num) as received_packets, sum(c2s_pkt_num+s2c_pkt_num) as packets from session_intercept_hits_log  where $filter  group by policy_id,action order by hits desc limit 100";

        sql = sql.replaceAll("\\$filter", "__time >= '2019-08-14 03:00:00' and  __time <'2019-08-15 04:00:00'");
        //sql = sql.replaceAll("\\$filter", " __time >= TIMESTAMP '2019-08-14 03:00:00' and  __time < TIMESTAMP '2019-08-15 04:00:00'");

        url = url + "/?query=" + sql ;
        Map map = restTemplate.getForObject(url, Map.class);
        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }

    /**
     * 当前策略第一次与最近一次命中时间（最近30天）
     */
    @Test
    public void firstAndLatelyHitPolicyOf30Day(){
        String sql = "select policy_id,toDateTime(min(recv_time)) as first_used, toDateTime(max(recv_time)) as last_used from session_record_log where $filter and policy_id in (0,1,2) group by policy_id";

        sql = sql.replaceAll("\\$format", "FROM_UNIXTIME(max(recv_time))");
        sql = sql.replaceAll("\\$filter", "recv_time >= DATE(FROM_UNIXTIME(UNIX_TIMESTAMP(now())-3600*24*30))");
        sql = sql.replaceAll("\\$format", "toDateTime(max(recv_time))");
        sql = sql.replaceAll("\\$filter", "recv_time>= (today()-30)");

        url = url + "/?query=" + sql ;
        Map map = restTemplate.getForObject(url, Map.class);
        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }

    /**
     *  最近活跃源IP排名（默认最近5分钟）
     */
    @Test
    public void activeSourceIPLately5min(){
        String sql = "select source,sum(session_num) as sessions, sum(c2s_byte_num) as sent_bytes, sum(s2c_byte_num) as received_bytes,  sum(c2s_pkt_num) as sent_packets ,sum(s2c_pkt_num) as received_packets from top_internal_host_log where $filter group by source order by sessions desc limit 100";

        sql = sql.replaceAll("\\$filter", "__time=SUBDATE(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(now())/300)*300),interval 10 minute)");
        //sql = sql.replaceAll("\\$filter", "__time=TIME_SHIFT(TIME_FLOOR(CURRENT_TIMESTAMP,'PT5M'),'PT10M',-1)");

        url = url + "/?query=" + sql ;
        Map map = restTemplate.getForObject(url, Map.class);
        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }

    /**
     * 最近1小时活跃源IP排名（非全量将丢失精度)
     */
    @Test
    public void activeSourceIPLately1Hour(){
        String sql = "select source,sum(session_num) as sessions, sum(c2s_byte_num) as sent_bytes, sum(s2c_byte_num) as received_bytes,  sum(c2s_pkt_num) as sent_packets ,sum(s2c_pkt_num) as received_packets from top_internal_host_log where $filter group by source order by sessions desc limit 100";

        sql = sql.replaceAll("\\$filter", "__time >=  '2019-08-13 16:00:00' and  __time < '2019-08-13 17:00:00'");
        sql = sql.replaceAll("\\$filter", "__time >= TIMESTAMP '2019-08-13 16:00:00' and  __time < TIMESTAMP '2019-08-13 17:00:00'");

        url = url + "/?query=" + sql ;
        Map map = restTemplate.getForObject(url, Map.class);
        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }

    /**
     * 最近活跃目的IP排名（默认最近5分钟）
     */
    @Test
    public void activeDestinationIPOf5min(){
        String sql = "select destination ,sum(session_num) as sessions, sum(c2s_byte_num) as sent_bytes, sum(s2c_byte_num) as received_bytes,  sum(c2s_pkt_num) as sent_packets ,sum(s2c_pkt_num) as received_packets from top_external_host_log where group by destination order by sessions desc limit 100";

        sql = sql.replaceAll("\\$filter", " __time=ADDDATE(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(now())/300)*300),interval -10 minute)");
        //sql = sql.replaceAll("\\$filter", " __time=TIME_SHIFT(TIME_FLOOR(CURRENT_TIMESTAMP,'PT5M'),'PT10M',-1)");

        url = url + "/?query=" + sql ;
        Map map = restTemplate.getForObject(url, Map.class);
        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }
    /**
     * 最近活跃用户
     */
    @Test
    public void activeUserLately(){
        String sql = "select url,sum(session_num) as hits  from top_urls_log where $filter group by url order by hits desc limit 100";

        sql = sql.replaceAll("\\$filter", "__time = ADDDATE(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(NOW())/300)*300),interval -10 minute)");
        sql = sql.replaceAll("\\$filter", "__time=TIME_SHIFT(TIME_FLOOR(CURRENT_TIMESTAMP,'PT5M'),'PT10M',-1)");

        url = url + "/?query=" + sql ;
        Map map = restTemplate.getForObject(url, Map.class);
        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }

    /**
     * 最近1小时活跃用户排名
     */
    @Test
    public void activeUserOfLately1Hour(){
        String sql = "select  subscribe_id, sum(c2s_byte_num) as sent_bytes, sum(s2c_byte_num) as received_bytes, sum(c2s_byte_num+s2c_byte_num) as bytes from top_user_log where $filter  group by subscribe_id order by bytes desc limit 10";

        sql = sql.replaceAll("\\$filter", "__time >= ADDDATE(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(now())/3600)*3600),interval -1 hour) and __time < FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(now())/3600)*3600)");
        sql = sql.replaceAll("\\$filter", "__time >= TIMESTAMP '2019-08-15 01:00:00' and  __time < TIMESTAMP '2019-08-15 02:00:00'");

        url = url + "/?query=" + sql ;
        Map map = restTemplate.getForObject(url, Map.class);
        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }
    /**
     * 某些策略命中总计
     */
    @Test
    public void policyHitOfTotal(){
        String sql = "select policy_id,sum(hits) as hits from session_intercept_hits_log where policy_id in (0,1)  group by policy_id union all select policy_id,sum(hits) as hits from policy_hits_log where policy_id in (0,1)  group by policy_id";

        sql = sql.replaceAll("\\$filter", "");

        url = url + "/?query=" + sql ;
        Map map = restTemplate.getForObject(url, Map.class);
        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }

    /**
     * 某策略最近24小时命中计数变化（粒度为5分钟)
     */
    @Test
    public void policyHitOfLately24Hour(){
        String sql = "select * from ( select  $format as stat_time,sum(hits) as hits from session_intercept_hits_log where $filter and policy_id=0 group by $format  union all select $format as stat_time,sum(hits) as hits from  policy_hits_log where $filter and policy_id=0 group by $format )   limit 50000";

        sql = sql.replaceAll("\\$filter", "__time >= FROM_UNIXTIME(UNIX_TIMESTAMP(now())-86400) and __time <= now()");
        sql = sql.replaceAll("\\$format", "FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/300)*300)");
        //sql = sql.replaceAll("\\$filter", "__time >= TIME_SHIFT(CURRENT_TIMESTAMP,'PT1H',-24) and __time <= CURRENT_TIMESTAMP");
        //sql = sql.replaceAll("\\$format", "TIME_FORMAT(time_floor(__time,'PT5M'),'yyyy-MM-dd HH:mm:ss')");

        url = url + "/?query=" + sql ;
        Map map = restTemplate.getForObject(url, Map.class);
        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }
    /**
     *  最近某些策略命中计数（5分钟）
     */
    @Test
    public void policyHitOfLately5min(){
        String sql = "select policy_id,sum(hits) as hits from session_intercept_hits_log where $filter   and  policy_id in (0,1) group by policy_id union all select policy_id,sum(hits) as hits from policy_hits_log where   $filter and policy_id in (0,1)  group by policy_id";

        sql = sql.replaceAll("\\$filter", "__time=ADDDATE(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(now())/300)*300),interval -10 minute)");
        //sql = sql.replaceAll("\\$filter", "__time=TIME_SHIFT(TIME_FLOOR(CURRENT_TIMESTAMP,'PT5M'),'PT5M',-1)");

        url = url + "/?query=" + sql ;
        Map map = restTemplate.getForObject(url, Map.class);
        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }
    /**
     * 最近某些策略命中计数（最近1小时）
     */
    @Test
    public void policyHitOfLately1Hour(){
        String sql = "select policy_id,sum(hits) as hits from session_intercept_hits_log where $filter  and  policy_id in (0,1) group by policy_id union all select policy_id,sum(hits) as hits from  policy_hits_log where $filter and  policy_id in (0,1) group by policy_id";

        sql = sql.replaceAll("\\$filter", "__time >= FROM_UNIXTIME(UNIX_TIMESTAMP(now())-3600) and __time <= now()");
        //sql = sql.replaceAll("\\$filter", "__time >= TIME_SHIFT(CURRENT_TIMESTAMP,'PT1H',-1) and __time <= CURRENT_TIMESTAMP");

        url = url + "/?query=" + sql ;
        Map map = restTemplate.getForObject(url, Map.class);
        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }
}
