package com.mesalab.qgw.service;

import com.google.common.base.CharMatcher;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@EnableAutoConfiguration
public class ClickHouseTest extends GalaxyQGWApplicationTests {
    @Autowired
    public TestRestTemplate restTemplate;
    private String url = "http://127.0.0.1:9999";

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
        String sql = "select recv_time,subscribe_id, trans_proto, client_ip, server_ip, server_port,host,url,con_duration_ms,entrance_id,c2s_pkt_num, s2c_pkt_num, c2s_byte_num, s2c_byte_num FROM session_record_log where recv_time=(select max(recv_time) from session_record_log) order by recv_time desc" +
                " limit 10,30";
        url = url + "/?query=" + sql ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }



    @Test
    public void testSQLFromSessionRecordLogsQuqeryQueryHour() {
        String sql = "SELECT policy_id as compileId, recv_time as recvTime, trans_proto as transProto, server_ip as serverIp, client_ip as clientIp, server_port as serverPort, client_port as clientPort, pinningst, intercept_state as interceptState, ssl_server_side_latency as sslServerSideLatency, ssl_client_side_latency as sslClientSideLatency, ssl_server_side_version as sslServerSideVersion, ssl_client_side_version as sslClientSideVersion, ssl_cert_verify as sslCertVerify, stream_trace_id as streamTraceId, sni, addr_type as addrType, entrance_id as entranceId, device_id as deviceId, link_id as linkId, isp, encap_type as encapType, direction, stream_dir as streamDir, cap_ip as capIp, addr_list as addrList, server_location as serverLocation, client_location as clientLocation, client_asn as clientAsn, server_asn as serverAsn, subscribe_id as subscribeId, con_duration_ms as conDurationMs, category, protocol_id as protocolId, c2s_pkt_num as c2sPktNum, s2c_pkt_num as s2cPktNum, c2s_byte_num as c2sByteNum, s2c_byte_num as s2cByteNum, nas_ip as nasIp, framed_ip as framedIp, account from session_record_log where   recv_time between  1563435187 and 1563438787 order by     recv_time desc  limit  0,20";
        url = url + "/?query=" + sql ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }

    @Test
    public void testSQLFromSessionRecordLogsQueryMonth() {
        String sql = "SELECT policy_id as compileId, recv_time as recvTime, trans_proto as transProto, server_ip as serverIp, client_ip as clientIp, server_port as serverPort, client_port as clientPort, pinningst, intercept_state as interceptState, ssl_server_side_latency as sslServerSideLatency, ssl_client_side_latency as sslClientSideLatency, ssl_server_side_version as sslServerSideVersion, ssl_client_side_version as sslClientSideVersion, ssl_cert_verify as sslCertVerify, stream_trace_id as streamTraceId, sni, addr_type as addrType, entrance_id as entranceId, device_id as deviceId, link_id as linkId, isp, encap_type as encapType, direction, stream_dir as streamDir, cap_ip as capIp, addr_list as addrList, server_location as serverLocation, client_location as clientLocation, client_asn as clientAsn, server_asn as serverAsn, subscribe_id as subscribeId, con_duration_ms as conDurationMs, category, protocol_id as protocolId, c2s_pkt_num as c2sPktNum, s2c_pkt_num as s2cPktNum, c2s_byte_num as c2sByteNum, s2c_byte_num as s2cByteNum, nas_ip as nasIp, framed_ip as framedIp, account from session_record_log where   recv_time>=toStartOfDay(now())-2678400 and recv_time<toStartOfDay(now())  order by     recv_time desc  limit  100000,20";
        url = url + "/?query=" + sql ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }



    @Test
    public void testSQLFromSessionRecordLogsQueryExportMonth() {
        String sql = "SELECT policy_id as compileId, recv_time as recvTime, client_ip as clientIp, client_port as clientPort, server_ip as serverIp, server_port as serverPort, sni, pinningst, intercept_state as interceptState, ssl_cert_verify as sslCertVerify from session_record_log where    recv_time>=toStartOfDay(now())-2678400 and recv_time<toStartOfDay(now()) order by     recv_time desc  limit 0 , 100000";
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

    @Test
    public void testTopDomainsByBandwidthPacketsAndUniqueIp() {
        String sql = "SELECT host, SUM(coalesce(c2s_byte_num, 0)) as sent_bytes, SUM(coalesce(s2c_byte_num, 0)) as received_bytes, SUM(coalesce(c2s_byte_num, 0) + coalesce(s2c_byte_num, 0)) as bytes, SUM(coalesce(c2s_pkt_num, 0)) as sent_packets, SUM(coalesce(s2c_pkt_num, 0)) as received_packets, SUM(coalesce(c2s_pkt_num, 0) + coalesce(s2c_pkt_num, 0)) as packets,count(distinct(client_ip)) as nums  FROM session_record_log where  $filter and notEmpty(host) GROUP BY host ORDER BY bytes DESC limit 100";
        sql = sql.replaceAll("\\$filter", "recv_time>=toStartOfDay(now())-604800");
        url = url + "/?query=" + sql ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map) +  map.get("status"));

        Assert.assertEquals((int)map.get("status"), 200);

    }


    @Test
    public void testTopUrlsByHitshPacketsAndUniqueIp() {
        String sql = " select $time_granularity as stat_time,host, count(distinct(client_ip) ) as nums  from session_record_log where $filter and host in (select host from session_record_log where $filter and notEmpty(host) group by host order by count(*)  desc limit 10 ) group by stat_time, host order by stat_time asc";
        sql = sql.replaceAll("\\$filter", "recv_time>=toStartOfDay(now())-604800");
        sql = sql.replaceAll("\\$time_granularity", " toDateTime(intDiv(toUInt32(toDateTime(toDateTime(recv_time))), 300)*300)");
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
