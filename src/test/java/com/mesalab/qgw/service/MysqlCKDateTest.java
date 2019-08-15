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

    private String executeTime = "toDateTime('2019-06-01 18:37:45')";

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
     *      通联日志检索
     */
    @Test
    public void tonlyLogRetrieval(){
        String sql = "select recv_time,subscribe_id, trans_proto, client_ip, server_ip, server_port,host,url,con_duration_ms,entrance_id,c2s_pkt_num, s2c_pkt_num, c2s_byte_num, s2c_byte_num FROM session_record_log where recv_time>= toDateTime('2019-05-31 00:00:00') and recv_time< toDateTime('2019-06-01 00:00:00') and entrance_id=1 and client_ip like '221.181%' and server_ip='60.247.81.34' order by recv_time desc limit 0,20";
        String sqlBegin = "select recv_time,subscribe_id, trans_proto, client_ip, server_ip, server_port,host,url,con_duration_ms,entrance_id,c2s_pkt_num, s2c_pkt_num, c2s_byte_num, s2c_byte_num FROM session_record_log where recv_time>= UNIX_TIMESTAMP(DATE_ADD(STR_TO_DATE('2019-06-01 05:00:00', '%Y-%m-%d 00:00:00'),INTERVAL -1 DAY)) and recv_time< UNIX_TIMESTAMP(STR_TO_DATE('2019-06-01 05:00:00', '%Y-%m-%d 00:00:00')) and entrance_id=1 and client_ip like '221.181%' and server_ip='60.247.81.34' order by recv_time desc limit 0,20 ";
        url = url + "/?query=" + sqlBegin ;
        Map map = restTemplate.getForObject(url, Map.class);
        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }

    /**
     *  策略日志检索
     */
    @Test
    public void policyLogRetrieval(){
        String sql = "select recv_time,policy_id,subscribe_id, trans_proto, client_ip, server_ip, server_port,host,url,con_duration_ms,entrance_id,action,c2s_pkt_num, s2c_pkt_num, c2s_byte_num, s2c_byte_num FROM policy_event_log where recv_time>= toDateTime('2019-05-31 00:00:00') and recv_time< toDateTime('2019-06-01 00:00:00') and entrance_id=1 and client_ip like '221.181%' and server_ip='60.247.81.34' order by recv_time desc limit 0,20";
        String sqlBegin = "select recv_time,policy_id,subscribe_id, trans_proto, client_ip, server_ip, server_port,host,url,con_duration_ms,entrance_id,action,c2s_pkt_num, s2c_pkt_num, c2s_byte_num, s2c_byte_num FROM policy_event_log where recv_time>= UNIX_TIMESTAMP('2019-05-31 00:00:00') and recv_time< UNIX_TIMESTAMP('2019-06-01 00:00:00') and entrance_id=1 and client_ip like '221.181%' and server_ip='60.247.81.34' order by recv_time desc limit 0,20";
        url = url + "/?query=" + sqlBegin ;
        Map map = restTemplate.getForObject(url, Map.class);
        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }

    /**
     *  策略日志检索
     */
    @Test
    public void radiusLogRetrieval(){
        String sql = "select recv_time,account,nas_ip,framed_ip,packet_type, trans_proto, client_ip, server_ip, server_port FROM radius_log WHERE recv_time>= toDateTime('2019-05-31 00:00:00') and recv_time< toDateTime('2019-06-01 00:00:00') and nas_ip='178.89.12.222' order by recv_time desc limit 0,20";
        String sqlBegin = "select recv_time,account,nas_ip,framed_ip,packet_type, trans_proto, client_ip, server_ip, server_port FROM radius_log WHERE recv_time>= UNIX_TIMESTAMP('2019-05-31 00:00:00') and recv_time< UNIX_TIMESTAMP(STR_TO_DATE('2019-06-01 00:00:00','%Y-%m-%d %H:%i:%s')) and nas_ip='178.89.12.222' order by recv_time desc limit 0,20";
        url = url + "/?query=" + sqlBegin ;
        Map map = restTemplate.getForObject(url, Map.class);
        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }
    /**
     *  当前策略最近命中时间（最近30天）
     */
    @Test
    public void currentPolicyHits(){
        String sql = "select  policy_id , toDateTime(max(recv_time)) last_used from session_record_log where recv_time>= toDateTime((today()-30)) and policy_id in (0,1,2) group by policy_id";
        String sqlBegin = "select  policy_id , toDateTime(max(recv_time)) last_used from session_record_log where recv_time>= DATE_ADD(DATE('2019-06-06 19:11:12'), INTERVAL -30 DAY) and policy_id in (0,1,2) group by policy_id";
        url = url + "/?query=" + sqlBegin ;
        Map map = restTemplate.getForObject(url, Map.class);
        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }

    /**
     *  Internal Hosts 报告
     */
    @Test
    public void internalHosts(){
        String sql = "select client_ip,count(*) as sessions from session_record_log where recv_time>= toStartOfDay(toDateTime('2019-06-01 18:37:45'))-604800 and recv_time< toStartOfDay(toDateTime('2019-06-01 18:37:45')) group by client_ip order by sessions desc limit 0,100";
        String sqlBegin = "select server_ip,count(*) as sessions from session_record_log where recv_time>= UNIX_TIMESTAMP(DATE_FORMAT('2019-06-01 18:37:45', '%Y-%m-%d 00:00:00'))-604800 and recv_time< UNIX_TIMESTAMP(DATE_FORMAT('2019-06-01 18:37:45', '%Y-%m-%d 00:00:00')) group by server_ip order by sessions desc limit 0,100";
        url = url + "/?query=" + sql ;
        Map map = restTemplate.getForObject(url, Map.class);
        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }

    /**
     *  External Hosts 报告
     */
    @Test
    public void externalHosts(){
        String sql = "select server_ip,count(*) as sessions from session_record_log where recv_time>= toStartOfDay(toDateTime('2019-06-01 18:37:45'))-604800 and recv_time< toStartOfDay(toDateTime('2019-06-01 18:37:45')) group by server_ip order by sessions desc limit 0,100";
        String sqlBegin = "select server_ip,count(*) as sessions from session_record_log where recv_time>= UNIX_TIMESTAMP(DATE_FORMAT('2019-06-01 18:37:45', '%Y-%m-%d 00:00:00'))-604800 and recv_time< UNIX_TIMESTAMP(DATE_FORMAT('2019-06-01 18:37:45', '%Y-%m-%d 00:00:00')) group by server_ip order by sessions desc limit 0,100";
        url = url + "/?query=" + sqlBegin ;
        Map map = restTemplate.getForObject(url, Map.class);
        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }
    /**
     *  Domains 报告
     */
    @Test
    public void domains(){
        String sql = "SELECT domain AS domain,SUM(coalesce(c2s_byte_num, 0)) AS sent_bytes,SUM(coalesce(s2c_byte_num, 0)) AS received_bytes,SUM(coalesce(c2s_byte_num, 0)+coalesce(s2c_byte_num, 0)) AS bytes FROM session_record_log WHERE recv_time >= toStartOfDay(toDateTime('2019-06-01 18:37:45'))-86400 AND recv_time < toStartOfDay(toDateTime('2019-06-01 18:37:45')) and notEmpty(domain) GROUP BY domain ORDER BY bytes DESC LIMIT 100";
        String sqlBegin = "SELECT domain AS domain,SUM(coalesce(c2s_byte_num, 0)) AS sent_bytes,SUM(coalesce(s2c_byte_num, 0)) AS received_bytes,SUM(coalesce(c2s_byte_num, 0)+coalesce(s2c_byte_num, 0)) AS bytes FROM session_record_log WHERE recv_time >= UNIX_TIMESTAMP(DATE_FORMAT('2019-06-01 18:37:45', '%Y-%m-%d 00:00:00'))-604800 AND recv_time < UNIX_TIMESTAMP(DATE_FORMAT('2019-06-01 18:37:45', '%Y-%m-%d 00:00:00')) and notEmpty(domain) GROUP BY domain ORDER BY bytes DESC LIMIT 100";
        url = url + "/?query=" + sql ;
        Map map = restTemplate.getForObject(url, Map.class);
        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }
   /**
     *  HTTP/HTTPS URLS 报告
     */
    @Test
    public void httpUrls(){
        String sql = "SELECT url AS url,count(*) AS sessions FROM policy_event_log WHERE recv_time >= toStartOfDay(toDateTime('2019-06-01 18:37:45'))-86400 AND recv_time < toStartOfDay(toDateTime('2019-06-01 18:37:45')) and notEmpty(url) GROUP BY url ORDER BY sessions DESC LIMIT 100";
        String sqlBegin = "SELECT url AS url,count(*) AS sessions FROM policy_event_log WHERE recv_time >= FROM_UNIXTIME(UNIX_TIMESTAMP(DATE_FORMAT('2019-06-01 18:37:45', '%Y-%m-%d 00:00:00')))-86400 AND recv_time < FROM_UNIXTIME(DATE_FORMAT('2019-06-01 18:37:45', '%Y-%m-%d 00:00:00')) and notEmpty(url) GROUP BY url ORDER BY sessions DESC LIMIT 100";
        url = url + "/?query=" + sqlBegin ;
        Map map = restTemplate.getForObject(url, Map.class);
        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }
   /**
     *  预制User 报告1
     */
    @Test
    public void userBySession(){
        String sql = "select subscribe_id as user,count(*) as sessions from session_record_log where recv_time>= toStartOfDay(toDateTime('2019-06-01 18:37:45'))-604800 and recv_time< toStartOfDay(toDateTime('2019-06-01 18:37:45'))  and notEmpty(user) group by subscribe_id order by sessions desc limit 0,100";
        String sqlBegin = "select subscribe_id as user,count(*) as sessions from session_record_log where recv_time>= UNIX_TIMESTAMP(DATE_FORMAT('2019-06-01 18:37:45', '%Y-%m-%d 00:00:00'))-604800 and recv_time< UNIX_TIMESTAMP(DATE_FORMAT('2019-06-01 18:37:45', '%Y-%m-%d 00:00:00'))  and notEmpty(user) group by subscribe_id order by sessions desc limit 0,100";
        url = url + "/?query=" + sql ;
        Map map = restTemplate.getForObject(url, Map.class);
        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }
   /**
     *  预制User 报告2
     */
    @Test
    public void userByBandWith(){
        String sql = "SELECT subscribe_id as user,SUM(coalesce(c2s_byte_num, 0)) AS sent_bytes,SUM(coalesce(s2c_byte_num, 0)) AS received_bytes,SUM(coalesce(c2s_byte_num, 0)+coalesce(s2c_byte_num, 0)) AS bytes FROM session_record_log WHERE recv_time>= toStartOfDay(toDateTime('2019-06-01 18:37:45'))-604800 and recv_time< toStartOfDay(toDateTime('2019-06-01 18:37:45')) and notEmpty(user) GROUP BY user ORDER BY bytes  DESC LIMIT 100";
        String sqlBegin = "SELECT subscribe_id as user,SUM(coalesce(c2s_byte_num, 0)) AS sent_bytes,SUM(coalesce(s2c_byte_num, 0)) AS received_bytes,SUM(coalesce(c2s_byte_num, 0)+coalesce(s2c_byte_num, 0)) AS bytes FROM session_record_log WHERE recv_time>= UNIX_TIMESTAMP(DATE_FORMAT('2019-06-01 18:37:45', '%Y-%m-%d 00:00:00'))-604800 and recv_time< UNIX_TIMESTAMP(DATE_FORMAT('2019-06-01 18:37:45', '%Y-%m-%d 00:00:00')) and notEmpty(user) GROUP BY user ORDER BY bytes  DESC LIMIT 100";
        url = url + "/?query=" + sql ;
        Map map = restTemplate.getForObject(url, Map.class);
        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }

    /**
     * 测试粒度30s
     */
    @Test
    public void testDateGranularity30S(){
        String sqlBegin = "select FROM_UNIXTIME(FLOOR(recv_time/30)*30) as time30,sum(c2s_pkt_num) FROM session_record_log where recv_time>= UNIX_TIMESTAMP(DATE_FORMAT(ADDDATE(DATE('2019-07-20 10:08:00'),INTERVAL -1 DAY),'%Y-%m-%d %H:%i:%s')) and recv_time< UNIX_TIMESTAMP(DATE('2019-07-20 10:08:00')) and entrance_id=1 and client_ip like '221.181%' and server_ip='60.247.81.34'  group by time30  order by time30 ASC limit 0,20";
        url = url + "/?query=" + sqlBegin ;
        Map map = restTemplate.getForObject(url, Map.class);
        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }

    /**
     * 测试粒度30s
     */
    @Test
    public void testDateGraularity5min(){
        String sqlBegin = "select FROM_UNIXTIME(FLOOR(recv_time/300)*300) as time300,sum(c2s_pkt_num) FROM session_record_log where recv_time>= UNIX_TIMESTAMP(DATE_FORMAT(ADDDATE(DATE('2019-07-20 10:08:00'),INTERVAL -1 DAY),'%Y-%m-%d %H:%i:%s')) and recv_time< UNIX_TIMESTAMP(DATE('2019-07-20 10:08:00')) and entrance_id=1 and client_ip like '221.181%' and server_ip='60.247.81.34'  group by time300  order by time300 ASC limit 0,20";
        url = url + "/?query=" + sqlBegin ;
        Map map = restTemplate.getForObject(url, Map.class);
        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }

}
