package com.mesalab.qgw.service;

import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.configuration.TestProperties;
import com.mesalab.common.utils.JsonMapper;
import com.zdjizhi.utils.DateUtils;
import com.zdjizhi.utils.StringUtil;
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
public class PolicyEventLogQueryTest extends GalaxyQGWApplicationTests {
    @Autowired
    private TestProperties properties;

    @Autowired
    public TestRestTemplate restTemplate;


    @Before
    public void testBefore() {
        log.info("=================================================CLICKHOUSE TEST BEGIN=================================================");
    }

    @Test
    public void testHttpGetMethod() {
        Map map = restTemplate.getForObject("http://" + properties.getUrl(), Map.class);

        log.info(JsonMapper.toJsonString(map));

        Assert.assertEquals((int)map.get("status"), 200);
    }




    @Test
    public void testQueryWithoutFilter() {
        String sql = "SELECT policy_id as compileId, recv_time as recvTime, trans_proto as transProto, server_ip as serverIp, client_ip as clientIp, server_port as serverPort, client_port as clientPort, pinningst, intercept_state as interceptState, ssl_server_side_latency as sslServerSideLatency, ssl_client_side_latency as sslClientSideLatency, ssl_server_side_version as sslServerSideVersion, ssl_client_side_version as sslClientSideVersion, ssl_cert_verify as sslCertVerify, stream_trace_id as streamTraceId, sni, addr_type as addrType, entrance_id as entranceId, device_id as deviceId, link_id as linkId, isp, encap_type as encapType, direction, stream_dir as streamDir, cap_ip as capIp, addr_list as addrList, server_location as serverLocation, client_location as clientLocation, client_asn as clientAsn, server_asn as serverAsn, subscribe_id as subscribeId, con_duration_ms as conDurationMs, category, protocol_id as protocolId, c2s_pkt_num as c2sPktNum, s2c_pkt_num as s2cPktNum, c2s_byte_num as c2sByteNum, s2c_byte_num as s2cByteNum, nas_ip as nasIp, framed_ip as framedIp, account " +
                "from policy_event_log where $time_range order by  recv_time desc  limit  0,20";
        sql = sql.replaceAll("\\$time_range", getTimeExpr());
        String url = properties.getUrl() + "/?query=" + sql ;

        Map map = null;
        try {
            map = restTemplate.getForObject("http://" + url, Map.class);
        } catch (Exception e) {
            e.printStackTrace();
        }

        log.info(JsonMapper.toJsonString(map));

        Assert.assertEquals((int)map.get("status"), 200);
    }

    @Test
    public void testQueryAddrTypeFilter() {
        String sql = "SELECT policy_id as compileId, recv_time as recvTime, trans_proto as transProto, server_ip as serverIp, client_ip as clientIp, server_port as serverPort, client_port as clientPort, pinningst, intercept_state as interceptState, ssl_server_side_latency as sslServerSideLatency, ssl_client_side_latency as sslClientSideLatency, ssl_server_side_version as sslServerSideVersion, ssl_client_side_version as sslClientSideVersion, ssl_cert_verify as sslCertVerify, stream_trace_id as streamTraceId, sni, addr_type as addrType, entrance_id as entranceId, device_id as deviceId, link_id as linkId, isp, encap_type as encapType, direction, stream_dir as streamDir, cap_ip as capIp, addr_list as addrList, server_location as serverLocation, client_location as clientLocation, client_asn as clientAsn, server_asn as serverAsn, subscribe_id as subscribeId, con_duration_ms as conDurationMs, category, protocol_id as protocolId, c2s_pkt_num as c2sPktNum, s2c_pkt_num as s2cPktNum, c2s_byte_num as c2sByteNum, s2c_byte_num as s2cByteNum, nas_ip as nasIp, framed_ip as framedIp, account " +
                "from policy_event_log where $time_range and addr_type=4 order by  recv_time desc  limit  0,20";
        sql = sql.replaceAll("\\$time_range", getTimeExpr());
        String url = properties.getUrl() + "/?query=" + sql ;

        Map map = null;
        try {
            map = restTemplate.getForObject("http://" + url, Map.class);
        } catch (Exception e) {
            e.printStackTrace();
        }

        log.info(JsonMapper.toJsonString(map));

        Assert.assertEquals((int)map.get("status"), 200);
    }


    @Test
    public void testQueryTransProtoFilter() {
        String sql = "SELECT policy_id as compileId, recv_time as recvTime, trans_proto as transProto, server_ip as serverIp, client_ip as clientIp, server_port as serverPort, client_port as clientPort, pinningst, intercept_state as interceptState, ssl_server_side_latency as sslServerSideLatency, ssl_client_side_latency as sslClientSideLatency, ssl_server_side_version as sslServerSideVersion, ssl_client_side_version as sslClientSideVersion, ssl_cert_verify as sslCertVerify, stream_trace_id as streamTraceId, sni, addr_type as addrType, entrance_id as entranceId, device_id as deviceId, link_id as linkId, isp, encap_type as encapType, direction, stream_dir as streamDir, cap_ip as capIp, addr_list as addrList, server_location as serverLocation, client_location as clientLocation, client_asn as clientAsn, server_asn as serverAsn, subscribe_id as subscribeId, con_duration_ms as conDurationMs, category, protocol_id as protocolId, c2s_pkt_num as c2sPktNum, s2c_pkt_num as s2cPktNum, c2s_byte_num as c2sByteNum, s2c_byte_num as s2cByteNum, nas_ip as nasIp, framed_ip as framedIp, account " +
                "from policy_event_log where $time_range and trans_proto in ('IPv4_TCP', 'IPv6_TCP') order by  recv_time desc  limit  0,20";
        sql = sql.replaceAll("\\$time_range", getTimeExpr());
        String url = properties.getUrl() + "/?query=" + sql ;

        Map map = null;
        try {
            map = restTemplate.getForObject("http://" + url, Map.class);
        } catch (Exception e) {
            e.printStackTrace();
        }

        log.info(JsonMapper.toJsonString(map));

        Assert.assertEquals((int)map.get("status"), 200);
    }


    @Test
    public void testQueryServerPortFilter() {
        String sql = "SELECT policy_id as compileId, recv_time as recvTime, trans_proto as transProto, server_ip as serverIp, client_ip as clientIp, server_port as serverPort, client_port as clientPort, pinningst, intercept_state as interceptState, ssl_server_side_latency as sslServerSideLatency, ssl_client_side_latency as sslClientSideLatency, ssl_server_side_version as sslServerSideVersion, ssl_client_side_version as sslClientSideVersion, ssl_cert_verify as sslCertVerify, stream_trace_id as streamTraceId, sni, addr_type as addrType, entrance_id as entranceId, device_id as deviceId, link_id as linkId, isp, encap_type as encapType, direction, stream_dir as streamDir, cap_ip as capIp, addr_list as addrList, server_location as serverLocation, client_location as clientLocation, client_asn as clientAsn, server_asn as serverAsn, subscribe_id as subscribeId, con_duration_ms as conDurationMs, category, protocol_id as protocolId, c2s_pkt_num as c2sPktNum, s2c_pkt_num as s2cPktNum, c2s_byte_num as c2sByteNum, s2c_byte_num as s2cByteNum, nas_ip as nasIp, framed_ip as framedIp, account " +
                "from policy_event_log where $time_range and server_port in (443,80) order by  recv_time desc  limit  0,20";
        sql = sql.replaceAll("\\$time_range", getTimeExpr());
        String url = properties.getUrl() + "/?query=" + sql ;

        Map map = null;
        try {
            map = restTemplate.getForObject("http://" + url, Map.class);
        } catch (Exception e) {
            e.printStackTrace();
        }

        log.info(JsonMapper.toJsonString(map));

        Assert.assertEquals((int)map.get("status"), 200);
    }

    @Test
    public void testQueryClientIpFilter() {
        String sql = "SELECT policy_id as compileId, recv_time as recvTime, trans_proto as transProto, server_ip as serverIp, client_ip as clientIp, server_port as serverPort, client_port as clientPort, pinningst, intercept_state as interceptState, ssl_server_side_latency as sslServerSideLatency, ssl_client_side_latency as sslClientSideLatency, ssl_server_side_version as sslServerSideVersion, ssl_client_side_version as sslClientSideVersion, ssl_cert_verify as sslCertVerify, stream_trace_id as streamTraceId, sni, addr_type as addrType, entrance_id as entranceId, device_id as deviceId, link_id as linkId, isp, encap_type as encapType, direction, stream_dir as streamDir, cap_ip as capIp, addr_list as addrList, server_location as serverLocation, client_location as clientLocation, client_asn as clientAsn, server_asn as serverAsn, subscribe_id as subscribeId, con_duration_ms as conDurationMs, category, protocol_id as protocolId, c2s_pkt_num as c2sPktNum, s2c_pkt_num as s2cPktNum, c2s_byte_num as c2sByteNum, s2c_byte_num as s2cByteNum, nas_ip as nasIp, framed_ip as framedIp, account " +
                "from policy_event_log where $time_range and  client_ip like '123.162.185%' and  server_port in (443,80) order by  recv_time desc  limit  0,20";
        sql = sql.replaceAll("\\$time_range", getTimeExpr());
        String url = properties.getUrl() + "/?query=" + sql ;

        Map map = null;
        try {
            map = restTemplate.getForObject("http://" + url, Map.class);
        } catch (Exception e) {
            e.printStackTrace();
        }

        log.info(JsonMapper.toJsonString(map));

        Assert.assertEquals((int)map.get("status"), 200);
    }


    @Test
    public void testQueryServerIpAndDomainFilter() {
        String sql = "SELECT policy_id as compileId, recv_time as recvTime, trans_proto as transProto, server_ip as serverIp, client_ip as clientIp, server_port as serverPort, client_port as clientPort, pinningst, intercept_state as interceptState, ssl_server_side_latency as sslServerSideLatency, ssl_client_side_latency as sslClientSideLatency, ssl_server_side_version as sslServerSideVersion, ssl_client_side_version as sslClientSideVersion, ssl_cert_verify as sslCertVerify, stream_trace_id as streamTraceId, sni, addr_type as addrType, entrance_id as entranceId, device_id as deviceId, link_id as linkId, isp, encap_type as encapType, direction, stream_dir as streamDir, cap_ip as capIp, addr_list as addrList, server_location as serverLocation, client_location as clientLocation, client_asn as clientAsn, server_asn as serverAsn, subscribe_id as subscribeId, con_duration_ms as conDurationMs, category, protocol_id as protocolId, c2s_pkt_num as c2sPktNum, s2c_pkt_num as s2cPktNum, c2s_byte_num as c2sByteNum, s2c_byte_num as s2cByteNum, nas_ip as nasIp, framed_ip as framedIp, account " +
                "from policy_event_log where $time_range and  server_ip='202.36.54.179' and domain like '%google%' and  server_port in (443,80) order by  recv_time desc  limit  0,20";
        sql = sql.replaceAll("\\$time_range", getTimeExpr());
        String url = properties.getUrl() + "/?query=" + sql ;

        Map map = null;
        try {
            map = restTemplate.getForObject("http://" + url, Map.class);
        } catch (Exception e) {
            e.printStackTrace();
        }

        log.info(JsonMapper.toJsonString(map));

        Assert.assertEquals((int)map.get("status"), 200);
    }

    @Test
    public void testQueryServerIpFilter() {
        String sql = "SELECT policy_id as compileId, recv_time as recvTime, trans_proto as transProto, server_ip as serverIp, client_ip as clientIp, server_port as serverPort, client_port as clientPort, pinningst, intercept_state as interceptState, ssl_server_side_latency as sslServerSideLatency, ssl_client_side_latency as sslClientSideLatency, ssl_server_side_version as sslServerSideVersion, ssl_client_side_version as sslClientSideVersion, ssl_cert_verify as sslCertVerify, stream_trace_id as streamTraceId, sni, addr_type as addrType, entrance_id as entranceId, device_id as deviceId, link_id as linkId, isp, encap_type as encapType, direction, stream_dir as streamDir, cap_ip as capIp, addr_list as addrList, server_location as serverLocation, client_location as clientLocation, client_asn as clientAsn, server_asn as serverAsn, subscribe_id as subscribeId, con_duration_ms as conDurationMs, category, protocol_id as protocolId, c2s_pkt_num as c2sPktNum, s2c_pkt_num as s2cPktNum, c2s_byte_num as c2sByteNum, s2c_byte_num as s2cByteNum, nas_ip as nasIp, framed_ip as framedIp, account " +
                "from policy_event_log where $time_range and  server_ip='202.36.54.179' and  server_port in (443,80) order by  recv_time desc  limit  0,20";
        sql = sql.replaceAll("\\$time_range", getTimeExpr());
        String url = properties.getUrl() + "/?query=" + sql ;

        Map map = null;
        try {
            map = restTemplate.getForObject("http://" + url, Map.class);
        } catch (Exception e) {
            e.printStackTrace();
        }

        log.info(JsonMapper.toJsonString(map));

        Assert.assertEquals((int)map.get("status"), 200);
    }

    @Test
    public void testQueryServerIpAndPortFilter() {
        String sql = "SELECT policy_id as compileId, recv_time as recvTime, trans_proto as transProto, server_ip as serverIp, client_ip as clientIp, server_port as serverPort, client_port as clientPort, pinningst, intercept_state as interceptState, ssl_server_side_latency as sslServerSideLatency, ssl_client_side_latency as sslClientSideLatency, ssl_server_side_version as sslServerSideVersion, ssl_client_side_version as sslClientSideVersion, ssl_cert_verify as sslCertVerify, stream_trace_id as streamTraceId, sni, addr_type as addrType, entrance_id as entranceId, device_id as deviceId, link_id as linkId, isp, encap_type as encapType, direction, stream_dir as streamDir, cap_ip as capIp, addr_list as addrList, server_location as serverLocation, client_location as clientLocation, client_asn as clientAsn, server_asn as serverAsn, subscribe_id as subscribeId, con_duration_ms as conDurationMs, category, protocol_id as protocolId, c2s_pkt_num as c2sPktNum, s2c_pkt_num as s2cPktNum, c2s_byte_num as c2sByteNum, s2c_byte_num as s2cByteNum, nas_ip as nasIp, framed_ip as framedIp, account " +
                "from policy_event_log where $time_range and  server_ip='202.36.54.179' order by  recv_time desc  limit  0,20";
        sql = sql.replaceAll("\\$time_range", getTimeExpr());
        String url = properties.getUrl() + "/?query=" + sql ;

        Map map = null;
        try {
            map = restTemplate.getForObject("http://" + url, Map.class);
        } catch (Exception e) {
            e.printStackTrace();
        }

        log.info(JsonMapper.toJsonString(map));

        Assert.assertEquals((int)map.get("status"), 200);
    }



    @Test
    public void testQueryClientIpServerIpAndPortFilter() {
        String sql = "SELECT policy_id as compileId, recv_time as recvTime, trans_proto as transProto, server_ip as serverIp, client_ip as clientIp, server_port as serverPort, client_port as clientPort, pinningst, intercept_state as interceptState, ssl_server_side_latency as sslServerSideLatency, ssl_client_side_latency as sslClientSideLatency, ssl_server_side_version as sslServerSideVersion, ssl_client_side_version as sslClientSideVersion, ssl_cert_verify as sslCertVerify, stream_trace_id as streamTraceId, sni, addr_type as addrType, entrance_id as entranceId, device_id as deviceId, link_id as linkId, isp, encap_type as encapType, direction, stream_dir as streamDir, cap_ip as capIp, addr_list as addrList, server_location as serverLocation, client_location as clientLocation, client_asn as clientAsn, server_asn as serverAsn, subscribe_id as subscribeId, con_duration_ms as conDurationMs, category, protocol_id as protocolId, c2s_pkt_num as c2sPktNum, s2c_pkt_num as s2cPktNum, c2s_byte_num as c2sByteNum, s2c_byte_num as s2cByteNum, nas_ip as nasIp, framed_ip as framedIp, account " +
                "from policy_event_log where $time_range and  client_ip like '%117%' and server_ip='202.36.54.179' and  server_port in (443,80) order by  recv_time desc  limit  0,20";
        sql = sql.replaceAll("\\$time_range", getTimeExpr());
        String url = properties.getUrl() + "/?query=" + sql ;

        Map map = null;
        try {
            map = restTemplate.getForObject("http://" + url, Map.class);
        } catch (Exception e) {
            e.printStackTrace();
        }

        log.info(JsonMapper.toJsonString(map));

        Assert.assertEquals((int)map.get("status"), 200);
    }


    @Test
    public void testQuerySniFilter() {
        String sql = "SELECT policy_id as compileId, recv_time as recvTime, trans_proto as transProto, server_ip as serverIp, client_ip as clientIp, server_port as serverPort, client_port as clientPort, pinningst, intercept_state as interceptState, ssl_server_side_latency as sslServerSideLatency, ssl_client_side_latency as sslClientSideLatency, ssl_server_side_version as sslServerSideVersion, ssl_client_side_version as sslClientSideVersion, ssl_cert_verify as sslCertVerify, stream_trace_id as streamTraceId, sni, addr_type as addrType, entrance_id as entranceId, device_id as deviceId, link_id as linkId, isp, encap_type as encapType, direction, stream_dir as streamDir, cap_ip as capIp, addr_list as addrList, server_location as serverLocation, client_location as clientLocation, client_asn as clientAsn, server_asn as serverAsn, subscribe_id as subscribeId, con_duration_ms as conDurationMs, category, protocol_id as protocolId, c2s_pkt_num as c2sPktNum, s2c_pkt_num as s2cPktNum, c2s_byte_num as c2sByteNum, s2c_byte_num as s2cByteNum, nas_ip as nasIp, framed_ip as framedIp, account " +
                "from policy_event_log where $time_range  and sni like '%google%'  order by  recv_time desc  limit  0,20";
        sql = sql.replaceAll("\\$time_range", getTimeExpr());
        String url = properties.getUrl() + "/?query=" + sql ;

        Map map = null;
        try {
            map = restTemplate.getForObject("http://" + url, Map.class);
        } catch (Exception e) {
            e.printStackTrace();
        }

        log.info(JsonMapper.toJsonString(map));

        Assert.assertEquals((int)map.get("status"), 200);
    }


    @Test
    public void testQueryHostFilter() {
        String sql = "SELECT policy_id as compileId, recv_time as recvTime, trans_proto as transProto, server_ip as serverIp, client_ip as clientIp, server_port as serverPort, client_port as clientPort, pinningst, intercept_state as interceptState, ssl_server_side_latency as sslServerSideLatency, ssl_client_side_latency as sslClientSideLatency, ssl_server_side_version as sslServerSideVersion, ssl_client_side_version as sslClientSideVersion, ssl_cert_verify as sslCertVerify, stream_trace_id as streamTraceId, sni, addr_type as addrType, entrance_id as entranceId, device_id as deviceId, link_id as linkId, isp, encap_type as encapType, direction, stream_dir as streamDir, cap_ip as capIp, addr_list as addrList, server_location as serverLocation, client_location as clientLocation, client_asn as clientAsn, server_asn as serverAsn, subscribe_id as subscribeId, con_duration_ms as conDurationMs, category, protocol_id as protocolId, c2s_pkt_num as c2sPktNum, s2c_pkt_num as s2cPktNum, c2s_byte_num as c2sByteNum, s2c_byte_num as s2cByteNum, nas_ip as nasIp, framed_ip as framedIp, account " +
                "from policy_event_log where $time_range  and host like '%google%'  order by  recv_time desc  limit  0,20";
        sql = sql.replaceAll("\\$time_range", getTimeExpr());
        String url = properties.getUrl() + "/?query=" + sql ;

        Map map = null;
        try {
            map = restTemplate.getForObject("http://" + url, Map.class);
        } catch (Exception e) {
            e.printStackTrace();
        }

        log.info(JsonMapper.toJsonString(map));

        Assert.assertEquals((int)map.get("status"), 200);
    }



    @Test
    public void testQueryPolicyIdFilter() {
        String sql = "SELECT policy_id as compileId, recv_time as recvTime, trans_proto as transProto, server_ip as serverIp, client_ip as clientIp, server_port as serverPort, client_port as clientPort, pinningst, intercept_state as interceptState, ssl_server_side_latency as sslServerSideLatency, ssl_client_side_latency as sslClientSideLatency, ssl_server_side_version as sslServerSideVersion, ssl_client_side_version as sslClientSideVersion, ssl_cert_verify as sslCertVerify, stream_trace_id as streamTraceId, sni, addr_type as addrType, entrance_id as entranceId, device_id as deviceId, link_id as linkId, isp, encap_type as encapType, direction, stream_dir as streamDir, cap_ip as capIp, addr_list as addrList, server_location as serverLocation, client_location as clientLocation, client_asn as clientAsn, server_asn as serverAsn, subscribe_id as subscribeId, con_duration_ms as conDurationMs, category, protocol_id as protocolId, c2s_pkt_num as c2sPktNum, s2c_pkt_num as s2cPktNum, c2s_byte_num as c2sByteNum, s2c_byte_num as s2cByteNum, nas_ip as nasIp, framed_ip as framedIp, account " +
                "from policy_event_log where $time_range  and policy_id in (0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19)  order by  recv_time desc  limit  0,20";
        sql = sql.replaceAll("\\$time_range", getTimeExpr());
        String url = properties.getUrl() + "/?query=" + sql ;

        Map map = null;
        try {
            map = restTemplate.getForObject("http://" + url, Map.class);
        } catch (Exception e) {
            e.printStackTrace();
        }

        log.info(JsonMapper.toJsonString(map));

        Assert.assertEquals((int)map.get("status"), 200);
    }


    @Test
    public void testGroupByPolicyId() {
        String sql = "select policy_id,toDateTime(min(recv_time)) as first_used, toDateTime(max(recv_time)) as last_used, count(*) as hits" +
                " from policy_event_log where $time_range  group by policy_id  order by hits desc  limit  20";
        sql = sql.replaceAll("\\$time_range", getTimeExpr());
        String url = properties.getUrl() + "/?query=" + sql ;

        Map map = null;
        try {
            map = restTemplate.getForObject("http://" + url, Map.class);
        } catch (Exception e) {
            e.printStackTrace();
        }

        log.info(JsonMapper.toJsonString(map));

        Assert.assertEquals((int)map.get("status"), 200);
    }



    @After
    public void testAfter() {
        log.info("=================================================CLICKHOUSE TEST  END=================================================");

    }

    private String getExecuteTime(String executeTime) {

        if (StringUtil.isNotEmpty(DateUtils.convertStringToDate(executeTime, DateUtils.YYYY_MM_DD_HH24_MM_SS))) {
            return "toDateTime( '"+executeTime+"' )";
        } else {
            return "toDateTime( '"+DateUtils.getCurrentDate(DateUtils.YYYY_MM_DD_HH24_MM_SS)+"' )";
        }
    }

    private String getTimeExpr() {

        String timeRange = properties.getTimeRange();
        String executeTime = properties.getExecuteTIme();

        if (StringUtil.isBlank(timeRange)) {

            if (StringUtil.isNotBlank(executeTime)) {
                return " recv_time >= " + getExecuteTime(executeTime);
            } else {
                return " recv_time >= now() -300 and recv_time < now()";
            }

        } else {

            if (timeRange.equalsIgnoreCase("last 5 minutes")) {
                return " recv_time >= now() -300 and recv_time < now()";
            } else if (timeRange.equalsIgnoreCase("last 15 minutes")) {
                return " recv_time >= now() -900 and recv_time < now()";
            } else if (timeRange.equalsIgnoreCase("last 30 minutes")) {
                return " recv_time >= now() -1800 and recv_time < now()";
            } else if (timeRange.equalsIgnoreCase("last 1 hour")) {
                return " recv_time >= now() -3600 and recv_time < now()";
            } else if (timeRange.equalsIgnoreCase("last 3 hours")) {
                return " recv_time >= now() -10800 and recv_time < now()";
            } else if (timeRange.equalsIgnoreCase("last 6 hours")) {
                return " recv_time >= now() -21600 and recv_time < now()";
            } else if (timeRange.equalsIgnoreCase("last 12 hours")) {
                return " recv_time >= now() -43200 and recv_time < now()";
            } else if (timeRange.equalsIgnoreCase("last 24 hours")) {
                return " recv_time >= now() -86400 and recv_time < now()";
            } else if (timeRange.equalsIgnoreCase("last day")) {
                return " recv_time >= now() -86400 and recv_time < now()";
            } else if (timeRange.equalsIgnoreCase("last week")) {
                return " recv_time >= now() -604800 and recv_time < now()";
            } else if (timeRange.equalsIgnoreCase("last month")) {
                return " recv_time >= now() -2678400 and recv_time < now()";
            } else if (timeRange.equalsIgnoreCase("last quarter")) {
                return " recv_time >= now() -8035200 and recv_time < now()";
            } else if (timeRange.equalsIgnoreCase("last year")) {
                return " recv_time >= now() -3153600 and recv_time < now()";
            } else {

                throw new IllegalArgumentException("Not Support TimeRange Format，Please Reference API!");
            }

        }



    }



}
