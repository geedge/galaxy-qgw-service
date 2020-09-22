package com.mesalab.qgw.service.impl;


import com.google.common.collect.Maps;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.configuration.ClickHouseProperties;
import com.mesalab.common.configuration.DruidIoProperties;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.common.utils.SchemaCacheUtils;
import com.mesalab.knowledge.common.utils.HttpConfig;
import com.mesalab.qgw.model.api.ClickHouseHttpSource;
import com.zdjizhi.utils.Encodes;
import com.zdjizhi.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.JsonProperties;
import org.apache.avro.Schema;
import org.apache.avro.data.Json;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.*;

@Slf4j
@Service
public class TestSqlServiceImpl {

    @Autowired
    HttpClientService httpClientService;
    @Autowired
    private ClickHouseProperties clickHouseProperties;
    @Autowired
    public ApiServiceImpl apiServiceImpl;
    @Autowired
    public DruidIoProperties druidIoProperties;
    @Autowired
    public ClickHouseHttpSource clickHouseHttpSource;
    @Autowired
    HttpConfig httpConfig;
    private List errorDate;


    /**
     * 运行测试用例
     *
     * @return
     */
    public BaseResult runSql() {
        long start = System.currentTimeMillis();
        List list = ckSql();
        List listDruid = druidSql();
        list.addAll(listDruid);
        Map<String, String> resultTemp = null;
        BaseResult result = null;
        List<Object> data = new ArrayList<>();
        int countSuccess = 0;
        SimpleDateFormat format1 = new SimpleDateFormat("yyyy-MM-dd HH:00:00");
        SimpleDateFormat format2 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date date = new Date(System.currentTimeMillis());
        for (int i = 0; i < list.size(); i++) {
            String sql = String.valueOf(list.get(i));
            try {
                sql = sql.replaceAll("\\$start_time", "'" + format1.format(date) + "'");
                sql = sql.replaceAll("\\$end_time", "'" + format2.format(date) + "'");
                sql = URLEncoder.encode(sql, "utf-8").replaceAll("\\+", "%20");
            } catch (Exception e) {
                log.error("测试用例sql编码错误", e);
            }
            resultTemp = httpClientService.httpGet("http://127.0.0.1:9999/?query=" + sql, httpConfig.getServerResponseTimeOut());
            if (resultTemp.get("status").equals(String.valueOf(HttpStatus.SC_OK))) {
                resultTemp.remove("result");
                countSuccess++;
            } else {
                try {
                    resultTemp.put("query", URLDecoder.decode(sql, "UTF-8"));
                } catch (Exception e) {
                    log.error("sql查询结果处理异常", e);
                }
            }
            data.add(resultTemp);
        }

        Collections.sort(data, new Comparator<Object>() {

            @Override
            public int compare(Object o1, Object o2) {
                Map<String, Object> oneMap = (Map<String, Object>) o1;
                Map<String, Object> twoMap = (Map<String, Object>) o2;
                return Integer.valueOf(twoMap.get("status").toString()).compareTo(Integer.valueOf(oneMap.get("status").toString()));
            }
        });
        long cost = System.currentTimeMillis() - start;
        HashMap<String, Object> statistics = new HashMap<>();
        statistics.put("elapsed", String.format("%.2f", (double) cost / 1000));
        statistics.put("total", list.size());
        statistics.put("success", countSuccess);
        String message = countSuccess == list.size() ? "ok" : "error";
        result = BaseResultGenerator.success(message, data, statistics);
        return result;
    }

    /**
     * 运行schema校验
     *
     * @return
     */
    public BaseResult runSchema() {
        SchemaCacheUtils.removeAll();
        long start = System.currentTimeMillis();
        errorDate = new ArrayList();
        checkCKSchema("/config/avro/clickhouse");
        checkDruidSchema("/config/avro/druid");
        checkSchema("/config/avro/clickhouse");
        checkSchema("/config/avro/druid");
        long cost = System.currentTimeMillis() - start;
        HashMap<String, Object> statistics = new HashMap<>();
        statistics.put("elapsed", String.format("%.2f", (double) cost / 1000));
        String message = errorDate.size() == 0 ? "ok" : "error";
        return BaseResultGenerator.success(message, errorDate, statistics);
    }


    public List ckSql() {
        List<String> list = new ArrayList<>();
        list.add("select toDateTime(common_recv_time) as common_recv_time,common_subscriber_id, common_address_type, common_l4_protocol, common_client_ip, common_server_ip, common_server_port, ssl_sni, ssl_version from security_event_log where common_recv_time >= toDateTime($start_time) and common_recv_time< toDateTime($end_time) and common_client_ip like '49.7%' order by common_recv_time desc limit 0,20");
        list.add("select FROM_UNIXTIME(common_recv_time) as common_recv_time,common_subscriber_id, common_address_type, common_l4_protocol, common_client_ip, common_server_ip, common_server_port, ssl_sni, ssl_version from security_event_log where common_recv_time >= UNIX_TIMESTAMP($start_time) and common_recv_time< UNIX_TIMESTAMP($end_time) and common_client_ip like '49.7%' order by common_recv_time desc limit 0,20");
        list.add("select toDateTime(common_recv_time) as common_recv_time,common_subscriber_id, common_address_type, common_l4_protocol, common_client_ip, common_server_ip, common_server_port, http_host,http_url,http_user_agent from proxy_event_log where common_recv_time >= toDateTime($start_time) and common_recv_time< toDateTime($end_time) and http_host like '%joy.cn%' order by common_recv_time desc limit 0,20");
        list.add("select FROM_UNIXTIME(common_recv_time) as common_recv_time,common_subscriber_id, common_address_type, common_l4_protocol, common_client_ip, common_server_ip, common_server_port, http_host,http_url,http_user_agent from proxy_event_log where common_recv_time >= UNIX_TIMESTAMP($start_time) and common_recv_time< UNIX_TIMESTAMP($end_time) and http_host like '%joy.cn%' order by common_recv_time desc limit 0,20");
        list.add("select toDateTime(common_recv_time) as common_recv_time, common_address_type, common_l4_protocol, common_client_ip, common_server_ip, common_server_port, radius_framed_ip, radius_account from radius_record_log where common_recv_time >= toDateTime($start_time) and common_recv_time< toDateTime($start_time) and radius_account='T1yRd' order by common_recv_time desc limit 0,20");
        list.add("select FROM_UNIXTIME(common_recv_time) as common_recv_time,common_subscriber_id, common_address_type, common_l4_protocol, common_client_ip, common_server_ip, common_server_port, http_host,http_url,http_user_agent from proxy_event_log where common_recv_time >= UNIX_TIMESTAMP($start_time) and common_recv_time< UNIX_TIMESTAMP($end_time) and http_host like '%joy.cn%' order by common_recv_time desc limit 0,20");
        list.add("select toDateTime(common_recv_time) as common_recv_time, common_address_type, common_l4_protocol, common_client_ip, common_server_ip, common_server_port, radius_framed_ip, radius_account from radius_record_log where common_recv_time >= toDateTime($start_time) and common_recv_time< toDateTime($start_time) and radius_account='T1yRd' order by common_recv_time desc limit 0,20");
        list.add("select FROM_UNIXTIME(common_recv_time) as common_recv_time, common_address_type, common_l4_protocol, common_client_ip, common_server_ip, common_server_port, radius_framed_ip, radius_account from radius_record_log where common_recv_time >= UNIX_TIMESTAMP($start_time) and common_recv_time< UNIX_TIMESTAMP($end_time) and radius_account='T1yRd' order by common_recv_time desc limit 0,20");
        list.add("select FROM_UNIXTIME(common_recv_time) as common_recv_time,common_log_id,common_policy_id,common_subscriber_id,common_client_ip,common_client_port,common_l4_protocol,common_address_type,common_server_ip,common_server_port,common_action,common_direction,common_sled_ip,common_client_location,common_client_asn,common_server_location,common_server_asn,common_c2s_pkt_num,common_s2c_pkt_num,common_c2s_byte_num,common_s2c_byte_num,common_schema_type,common_sub_action,common_device_id, FROM_UNIXTIME(common_start_time) as common_start_time, FROM_UNIXTIME(common_end_time) as common_end_time,common_establish_latency_ms,common_con_duration_ms,common_stream_dir,common_stream_trace_id,http_url,http_host,http_domain,http_request_body,http_response_body,http_cookie,http_referer,http_user_agent,http_content_length,http_content_type,http_set_cookie,http_version,http_response_lantency_ms,http_action_file_size,http_session_duration_ms,mail_protocol_type,mail_account,mail_from_cmd,mail_to_cmd,mail_from,mail_to,mail_cc,mail_bcc,mail_subject,mail_attachment_name,mail_eml_file,dns_message_id,dns_qr,dns_opcode,dns_aa,dns_tc,dns_rd,dns_ra,dns_rcode,dns_qdcount,dns_ancount,dns_nscount,dns_arcount,dns_qname,dns_qtype,dns_qclass,dns_cname,dns_sub,dns_rr,ssl_sni,ssl_san,ssl_cn,ssl_pinningst,ssl_intercept_state,ssl_server_side_latency,ssl_client_side_latency,ssl_server_side_version,ssl_client_side_version,ssl_cert_verify,ssl_error,quic_version,quic_sni,quic_user_agent,ftp_account,ftp_url,ftp_content  from security_event_log where  common_recv_time  >= $start_time and common_recv_time < $end_time and  common_log_id in ( 289451559826124800 , 332 ) and (  common_server_ip like '192.168%' ) order by  common_recv_time desc  limit 100000");

        list.add("select count(1) from connection_record_log");
        list.add("select count(*) from connection_record_log");
        list.add("select * from " + clickHouseProperties.getDbname() + ".connection_record_log limit 1");

        //Radius 用户分析
        //1. radius账户申请客户端IP变化
        list.add("select framed_ip, arraySlice(groupUniqArray(concat(toString(event_timestamp),':', if(acct_status_type=1,'start','stop'))),1,100000) as timeseries \n" +
                "from radius_onff_log where event_timestamp >=toDateTime($start_time) and event_timestamp <toDateTime($end_time) and account='admin' group by framed_ip");
        list.add("select framed_ip, arraySlice(groupUniqArray(concat(toString(event_timestamp),':', if(acct_status_type=1,'start','stop'))),1,100000) as timeseries \n" +
                "from radius_onff_log where event_timestamp >=$start_time and event_timestamp < $end_time  and account='admin' group by framed_ip");
        //2. 用户IP承载用户变化
        list.add("select account, arraySlice(groupUniqArray(concat(toString(event_timestamp),':', if(acct_status_type=1,'start','stop'))),1,100000) as timeseries \n" +
                "from radius_onff_log where event_timestamp >= $start_time and event_timestamp < $end_time  and framed_ip='127.0.0.1' group by account");
        list.add("select account, arraySlice(groupUniqArray(concat(toString(event_timestamp),':', if(acct_status_type=1,'start','stop'))),1,100000) as timeseries \n" +
                "from radius_onff_log where event_timestamp >= $start_time and event_timestamp < $end_time  and framed_ip='127.0.0.1' group by account");
        //自定义报告API
        //一.  预置Internal Hosts 报告
        list.add("select common_client_ip, count(*) as sessions from connection_record_log where common_recv_time>= toStartOfDay(toDateTime($start_time))-604800 and common_recv_time< toStartOfDay(toDateTime($end_time)) group by common_client_ip order by sessions desc limit 0,100");
        //二. 预置External Hosts 报告
        list.add("select common_server_ip, count(*) as sessions from connection_record_log where common_recv_time>= toStartOfDay(toDateTime($start_time))-604800 and common_recv_time< toStartOfDay(toDateTime($start_time)) group by common_server_ip order by sessions desc limit 0,100");
        //三. 预置Domains报告
        list.add("select http_domain AS domain,SUM(coalesce(common_c2s_byte_num, 0)) AS sent_bytes,SUM(coalesce(common_s2c_byte_num, 0)) AS received_bytes,SUM(coalesce(common_c2s_byte_num, 0)+coalesce(common_s2c_byte_num, 0)) AS bytes FROM connection_record_log WHERE common_recv_time >= toStartOfDay(toDateTime($start_time))-86400 AND common_recv_time < toStartOfDay(toDateTime($start_time)) and notEmpty(domain) GROUP BY domain ORDER BY bytes DESC LIMIT 100");
        list.add("select toDateTime(intDiv(toUInt32(toDateTime(toDateTime(common_recv_time))), 300)*300) as stat_time, http_domain, uniq (common_client_ip) as nums  from connection_record_log where common_recv_time >= toStartOfDay(toDateTime($start_time))-86400 AND common_recv_time < toStartOfDay(toDateTime($start_time))  and http_domain in (select http_domain from connection_record_log where common_recv_time >= toStartOfDay(toDateTime($start_time))-86400 AND common_recv_time < toStartOfDay(toDateTime($start_time))  and notEmpty(http_domain) group by http_domain order by SUM(coalesce(common_c2s_byte_num, 0)+coalesce(common_s2c_byte_num, 0)) desc limit 10 ) group by toDateTime(intDiv(toUInt32(toDateTime(toDateTime(common_recv_time))), 300)*300), http_domain order by stat_time asc limit 500\n");
        list.add("SELECT http_host as host, SUM(coalesce(common_c2s_byte_num, 0)) AS sent_bytes,SUM(coalesce(common_s2c_byte_num, 0)) AS received_bytes,SUM(coalesce(common_c2s_byte_num, 0)+coalesce(common_s2c_byte_num, 0)) AS bytes FROM connection_record_log WHERE common_recv_time>= toStartOfDay(toDateTime($start_time))-604800 and common_recv_time< toStartOfDay(toDateTime($start_time))  and notEmpty(http_host) GROUP BY host ORDER BY bytes DESC  " +
                "union all " +
                "SELECT  'totals'  as host, SUM(coalesce(common_c2s_byte_num, 0)) AS sent_bytes, SUM(coalesce(common_s2c_byte_num, 0)) AS received_bytes, SUM(coalesce(common_c2s_byte_num, 0)+coalesce(common_s2c_byte_num, 0)) AS bytes from connection_record_log where common_recv_time>= toStartOfDay(toDateTime($start_time))-604800 and common_recv_time< toStartOfDay(toDateTime($start_time))  and notEmpty(http_host)");
        //四. 预置HTTP/HTTPS URLS报告
        list.add("SELECT http_url AS url,count(*) AS sessions FROM proxy_event_log WHERE common_recv_time >= toStartOfDay(toDateTime($start_time))-86400 AND common_recv_time < toStartOfDay(toDateTime($start_time)) and notEmpty(http_url) GROUP BY url ORDER BY sessions DESC LIMIT 100");
        list.add("select toDateTime(intDiv(toUInt32(toDateTime(toDateTime(common_recv_time))), 300)*300) as stat_time, http_url, count(distinct(common_client_ip)) as nums  from proxy_event_log where common_recv_time >= toStartOfDay(toDateTime($start_time))-86400 AND common_recv_time < toStartOfDay(toDateTime($start_time)) and http_url IN (select http_url from proxy_event_log where common_recv_time >= toStartOfDay(toDateTime($start_time))-86400 AND common_recv_time < toStartOfDay(toDateTime($start_time)) and notEmpty(http_url)  group by http_url order by count(*) desc limit 10 )group by toDateTime(intDiv(toUInt32(toDateTime(toDateTime(common_recv_time))), 300)*300), http_url order by stat_time asc limit 500");
        list.add("select common_subscriber_id as user, count(*) as sessions from connection_record_log where common_recv_time>= toStartOfDay(toDateTime($start_time))-604800 and common_recv_time< toStartOfDay(toDateTime($start_time))  and notEmpty(user) group by common_subscriber_id order by sessions desc limit 0,100");
        list.add("SELECT common_subscriber_id as user,SUM(coalesce(common_c2s_byte_num, 0)) AS sent_bytes,SUM(coalesce(common_s2c_byte_num, 0)) AS received_bytes,SUM(coalesce(common_c2s_byte_num, 0)+coalesce(common_s2c_byte_num, 0)) AS bytes FROM connection_record_log WHERE common_recv_time>= toStartOfDay(toDateTime($start_time))-604800 and common_recv_time< toStartOfDay(toDateTime($start_time)) and notEmpty(user) GROUP BY user ORDER BY bytes  DESC LIMIT 100");

        //RADIUS账户总计
        list.add("select count(distinct(framed_ip)) as active_ip_num , sum(acct_session_time) as online_duration from (select any(framed_ip) as framed_ip ,max(acct_session_time) as acct_session_time from radius_onff_log where account='000jS' and event_timestamp >= $start_time and event_timestamp < $end_time  group by acct_session_id)");
        //RADIUS账户IP详情
        list.add("select distinct(framed_ip) as framed_ip from radius_onff_log where account='000iS' and event_timestamp >= $start_time and event_timestamp < $end_time");
        //RADIUS账户访问详情
        list.add("select max(if(acct_status_type=1,event_timestamp,0)) as start_time,max(if(acct_status_type=2,event_timestamp,0)) as end_time, any(framed_ip) as ip,max(acct_session_time) as online_duration  from radius_onff_log where account='000jS' and event_timestamp >= $start_time and event_timestamp < $end_time  group by acct_session_id  order by start_time desc limit 200");
        //目标资源分析
        list.add("SELECT  domain, groupUniqArrayMerge(ip_list) as ip_list, groupUniqArrayMerge(cdn_list) as cdn_list, groupUniqArrayMerge(protocol_type_list) as protocol_type_list, groupUniqArrayMerge(port_list) as port_list  FROM security_website_domain_info where  stat_time >= $start_time and stat_time < $end_time and policy_id=0 group by domain");
        //某IP的关联域名关系
        list.add("SELECT  ip, FROM_UNIXTIME(max(stat_time)) as last_time, FROM_UNIXTIME(min(stat_time)) as first_time, groupUniqArrayMerge(domain_list) as domain_list, groupUniqArrayMerge(port_list) as port_list  FROM security_ip_info where  stat_time >= $start_time and stat_time < $end_time and ip='192.168.50.70' group by ip");
        //自定义函数测试
        list.add("SELECT policy_id, APPROX_COUNT_DISTINCT_DS_HLL(isp) as num  FROM proxy_event_hits_log where  __time >= '2020-04-05 00:00:00' and __time < '2020-05-05 00:00:00' and policy_id=0 group by policy_id");
        list.add("SELECT ip, IP_TO_CITY(ip) as location, IP_TO_GEO(ip) as geo from (SELECT policy_id, arrayJoin(groupUniqArrayMerge(ip_list)) as ip FROM proxy_ip_info where  stat_time >= $start_time and stat_time < $end_time and policy_id=0 group by policy_id)");
        list.add("select TIME_FLOOR_WITH_FILL(common_recv_time,'PT5M','previous') as stat_time from connection_record_log where  common_recv_time > $start_time and common_recv_time < $end_time  group by stat_time");

        return list;
    }

    public List druidSql() {
        List<String> list = new ArrayList<>();
        //安全策略命中统计
        //1. 某策略命中计数
        list.add("select policy_id, sum(hits) as hits from security_event_hits_log where __time >$start_time and __time <$end_time and policy_id=40  group by policy_id");
        list.add("select policy_id, sum(hits) as hits from security_event_hits_log where __time >$start_time and __time <$end_time and policy_id in (9,10,88,45)  group by policy_id");
        //2. 某策略命中计数趋势
        list.add("select TIME_FORMAT(time_floor(__time,'PT5M'),'yyyy-MM-dd HH:mm:ss') as start_time, sum(hits) as hits from security_event_hits_log where __time >= TIMESTAMP $start_time and __time < TIMESTAMP $end_time and policy_id=10 group by TIME_FORMAT(time_floor(__time,'PT5M'),'yyyy-MM-dd HH:mm:ss') limit 100");
        list.add("select DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/300)*300),'%Y-%m-%d %H:%i:%s') as start_time, sum(hits) as hits from security_event_hits_log where __time >= $start_time and __time < $end_time and policy_id=10 group by DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/300)*300),'%Y-%m-%d %H:%i:%s') limit 100");
        //3. 某策略命中时间（首次和最近一次)
        list.add("select policy_id,TIME_FORMAT(min(__time) ,'yyyy-MM-dd HH:mm:ss')  as first_used, TIME_FORMAT(max(__time) ,'yyyy-MM-dd HH:mm:ss') as last_used from security_event_hits_log where policy_id in (100,101 ,105, 102) group by policy_id");
        list.add("select policy_id, DATE_FORMAT(min(__time) ,'%Y-%m-%d %H:%i:%s')  as first_used,  DATE_FORMAT(max(__time) ,'%Y-%m-%d %H:%i:%s') as last_used from security_event_hits_log where policy_id in (100,101 ,105, 102) group by policy_id");
        //4. TopN 命中策略
        list.add("select policy_id, action, sum(hits) as hits from security_event_hits_log  where __time >=TIMESTAMP $start_time and __time <TIMESTAMP $end_time  group by policy_id, action order by hits desc limit 200");
        list.add("select policy_id, action, sum(hits) as hits from security_event_hits_log  where __time >=$start_time and __time <$end_time  group by policy_id, action order by hits desc limit 200");
        //二、 代理策略命中统计
        //1. 某策略命中计数
        list.add("select policy_id, sum(hits) as hits from proxy_event_hits_log where  __time >=$start_time and __time <$end_time and policy_id=100 group by policy_id");
        list.add("select policy_id, sum(hits) as hits from proxy_event_hits_log where  __time >=$start_time and __time <$end_time and policy_id=100 group by policy_id");
        //2. 某策略命中趋势
        list.add("select TIME_FORMAT(time_floor(__time,'PT5M'),'yyyy-MM-dd HH:mm:ss') as start_time, sum(hits) as hits from proxy_event_hits_log where __time >= TIMESTAMP $start_time and __time <TIMESTAMP $end_time and policy_id=100 group by TIME_FORMAT(time_floor(__time,'PT5M'),'yyyy-MM-dd HH:mm:ss') limit 101");
        list.add("select FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/300)*300) as start_time, sum(hits) as hits from proxy_event_hits_log where __time >= $start_time and __time < $end_time and policy_id=100 group by  FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/300)*300) limit 101");
        //3.某策略命中时间（首次和最近一次
        list.add("select policy_id,TIME_FORMAT(min(__time) ,'yyyy-MM-dd HH:mm:ss')  as first_used, TIME_FORMAT(max(__time) ,'yyyy-MM-dd HH:mm:ss') as last_used from  proxy_event_hits_log where policy_id in (100,101,102,105) group by policy_id");
        list.add("select policy_id, DATE_FORMAT(min(__time) ,'%Y-%m-%d %H:%i:%s')  as first_used,  DATE_FORMAT(max(__time) ,'%Y-%m-%d %H:%i:%s') as last_used from  proxy_event_hits_log where policy_id in (100,101,102,105) group by policy_id");
        //4. TopN 命中策略
        list.add("select policy_id, sub_action as action, sum(hits) as hits from proxy_event_hits_log  where __time >=TIMESTAMP $start_time and __time <TIMESTAMP $end_time  group by policy_id, sub_action order by hits desc limit 200");
        list.add("select policy_id, sub_action as action, sum(hits) as hits from proxy_event_hits_log  where __time >=$start_time and __time <$end_time  group by policy_id, sub_action order by hits desc limit 200");
        //5. Proxy 操纵动作命中计数
        list.add("select sub_action as action, sum(hits) as hits from proxy_event_hits_log where  __time >= TIMESTAMP $start_time and __time < TIMESTAMP $end_time  group by  sub_action");
        list.add("select sub_action as action, sum(hits) as hits from proxy_event_hits_log where  __time >= $start_time and __time < $end_time  group by  sub_action");
        //6. Proxy 操纵动作命中趋势
        list.add("select TIME_FORMAT(time_floor(__time,'PT5M'),'yyyy-MM-dd HH:mm:ss') as start_time, sub_action  as action, sum(hits) as hits from proxy_event_hits_log where  __time >=TIMESTAMP $start_time and __time <TIMESTAMP $end_time  group by TIME_FORMAT(time_floor(__time,'PT5M'),'yyyy-MM-dd HH:mm:ss') , sub_action  limit 100");
        list.add("select  DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/300)*300),'%Y-%m-%d %H:%i:%s') as start_time, sub_action  as action, sum(hits) as hits from proxy_event_hits_log where  __time >= $start_time and __time < $end_time  group by  DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/300)*300),'%Y-%m-%d %H:%i:%s'), sub_action  limit 100");
        //7. Proxy Pinning TIMESTAMP 计数
        list.add("SELECT sum(not_pinning_num) AS sessions, 'notPinningNum' AS type FROM traffic_metrics_log WHERE __time >= $start_time AND __time < $end_time UNION ALL SELECT sum(pinning_num) AS sessions, 'pinningNum' AS type FROM traffic_metrics_log WHERE __time >= $start_time AND __time < $end_time UNION ALL SELECT sum(maybe_pinning_num) AS sessions, 'maybePinningNum' AS type FROM traffic_metrics_log WHERE __time >= $start_time AND __time < $end_time");
        //8.Proxy Pinning计数趋势
        list.add("SELECT TIME_FORMAT( MILLIS_TO_TIMESTAMP( 1000 * (TIMESTAMP_TO_MILLIS(time_floor(0.001 * TIMESTAMP_TO_MILLIS( __time) * 1000,'PT300S'))/1000)),'YYYY-MM-dd HH:mm:ss') AS statisticTime, sum(pinning_num) AS sessions FROM traffic_metrics_log WHERE __time >= $start_time AND __time < $end_time GROUP BY TIME_FORMAT( MILLIS_TO_TIMESTAMP( 1000 * (TIMESTAMP_TO_MILLIS(time_floor(0.001 * TIMESTAMP_TO_MILLIS( __time) * 1000,'PT300S'))/1000)),'YYYY-MM-dd HH:mm:ss') LIMIT 100");
        list.add("SELECT TIME_FORMAT( MILLIS_TO_TIMESTAMP( 1000 * (TIMESTAMP_TO_MILLIS(time_floor(0.001 * TIMESTAMP_TO_MILLIS( __time) * 1000,'PT300S'))/1000)),'YYYY-MM-dd HH:mm:ss') AS statisticTime, sum(not_pinning_num) AS sessions FROM traffic_metrics_log WHERE __time>= $start_time AND __time < $end_time GROUP BY TIME_FORMAT( MILLIS_TO_TIMESTAMP( 1000 * (TIMESTAMP_TO_MILLIS(time_floor(0.001 * TIMESTAMP_TO_MILLIS( __time) * 1000,'PT300S'))/1000)),'YYYY-MM-dd HH:mm:ss') LIMIT 100");
        list.add("SELECT TIME_FORMAT( MILLIS_TO_TIMESTAMP( 1000 * (TIMESTAMP_TO_MILLIS(time_floor(0.001 * TIMESTAMP_TO_MILLIS( __time) * 1000,'PT300S'))/1000)),'YYYY-MM-dd HH:mm:ss') AS statisticTime, sum(maybe_pinning_num) AS sessions FROM traffic_metrics_log WHERE __time >= $start_time AND __time < $end_time GROUP BY TIME_FORMAT( MILLIS_TO_TIMESTAMP( 1000 * (TIMESTAMP_TO_MILLIS(time_floor(0.001 * TIMESTAMP_TO_MILLIS( __time) * 1000,'PT300S'))/1000)),'YYYY-MM-dd HH:mm:ss') LIMIT 100");
        //三、 Traffics-带宽统计
        //1. Traffic IN/OUT 计数
        //Bytes
        list.add("select sum(total_in_bytes) as traffic_in_bytes, sum(total_out_bytes) as traffic_out_bytes  from traffic_metrics_log where  __time >=TIMESTAMP  $start_time and __time <TIMESTAMP $end_time");
        list.add("select sum(total_in_bytes) as traffic_in_bytes, sum(total_out_bytes) as traffic_out_bytes  from traffic_metrics_log where  __time >= $start_time and __time < $end_time");
        //Packets
        list.add("select sum(total_in_packets) as traffic_in_packets, sum(total_out_packets) as traffic_out_packets  from traffic_metrics_log where  __time >=TIMESTAMP $start_time and __time <TIMESTAMP $end_time");
        list.add("select sum(total_in_packets) as traffic_in_packets, sum(total_out_packets) as traffic_out_packets  from traffic_metrics_log where  __time >= $start_time and __time < $end_time");
        //Sessions
        list.add("select sum(new_conn_num) as sessions  from traffic_metrics_log where  __time >=TIMESTAMP $start_time and __time <TIMESTAMP $end_time");
        list.add("select sum(new_conn_num) as sessions  from traffic_metrics_log where  __time >= $start_time and __time < $end_time");
        //2. Traffic IN/OUT 带宽趋势
        //Bytes
        list.add("select TIME_FORMAT(time_floor(__time,'PT30S'),'yyyy-MM-dd HH:mm:ss') as stat_time, 'traffic_in_bytes' as type, sum(total_in_bytes) as bytes from traffic_metrics_log where  __time >= $start_time and __time < $end_time group by TIME_FORMAT(time_floor(__time,'PT30S'),'yyyy-MM-dd HH:mm:ss')" +
                "union all\n" +
                "select TIME_FORMAT(time_floor(__time,'PT30S'),'yyyy-MM-dd HH:mm:ss') as stat_time, 'traffic_out_bytes' as type, sum(total_out_bytes) as bytes from traffic_metrics_log where  __time >= $start_time and __time < $end_time group by TIME_FORMAT(time_floor(__time,'PT30S'),'yyyy-MM-dd HH:mm:ss')");
        list.add("select DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/30)*30),'%Y-%m-%d %H:%i:%s') as stat_time, 'traffic_in_bytes' as type, sum(total_in_bytes) as bytes from traffic_metrics_log where  __time >= $start_time and __time < $end_time group by DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/30)*30),'%Y-%m-%d %H:%i:%s')\n" +
                "union all\n" +
                "select DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/30)*30),'%Y-%m-%d %H:%i:%s') as stat_time, 'traffic_out_bytes' as type, sum(total_out_bytes) as bytes from traffic_metrics_log where  __time >= $start_time and __time < $end_time group by DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/30)*30),'%Y-%m-%d %H:%i:%s')");
        //Packets
        list.add("select TIME_FORMAT(time_floor(__time,'PT30S'),'yyyy-MM-dd HH:mm:ss') as stat_time, 'traffic_in_packets' as type, sum(total_in_packets) as packets from traffic_metrics_log where  __time >=TIMESTAMP $start_time and __time <TIMESTAMP  $end_time group by TIME_FORMAT(time_floor(__time,'PT30S'),'yyyy-MM-dd HH:mm:ss')\n" +
                "union all\n" +
                "select TIME_FORMAT(time_floor(__time,'PT30S'),'yyyy-MM-dd HH:mm:ss') as stat_time, 'traffic_out_packets' as type, sum(total_out_packets) as packets from traffic_metrics_log where __time >=TIMESTAMP  $start_time and __time <TIMESTAMP  $end_time group by TIME_FORMAT(time_floor(__time,'PT30S'),'yyyy-MM-dd HH:mm:ss')");
        list.add("select DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/30)*30),'%Y-%m-%d %H:%i:%s') as stat_time, 'traffic_in_packets' as type, sum(total_in_packets) as packets from traffic_metrics_log where  __time >= $start_time and __time < $end_time group by DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/30)*30),'%Y-%m-%d %H:%i:%s')\n" +
                "union all\n" +
                "select DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/30)*30),'%Y-%m-%d %H:%i:%s') as stat_time, 'traffic_out_packets' as type, sum(total_out_packets) as packets from traffic_metrics_log where __time >= $start_time and __time < $end_time group by DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/30)*30),'%Y-%m-%d %H:%i:%s')");
        //Packets
        list.add("select TIME_FORMAT(time_floor(__time,'PT30S'),'yyyy-MM-dd HH:mm:ss') as stat_time, 'traffic_in_packets' as type, sum(total_in_packets) as packets from traffic_metrics_log where  __time >= $start_time and __time < $end_time group by TIME_FORMAT(time_floor(__time,'PT30S'),'yyyy-MM-dd HH:mm:ss')\n" +
                "union all\n" +
                "select TIME_FORMAT(time_floor(__time,'PT30S'),'yyyy-MM-dd HH:mm:ss') as stat_time, 'traffic_out_packets' as type, sum(total_out_packets) as packets from traffic_metrics_log where __time >= $start_time and __time < $end_time group by TIME_FORMAT(time_floor(__time,'PT30S'),'yyyy-MM-dd HH:mm:ss')");
        list.add("select DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/30)*30),'%Y-%m-%d %H:%i:%s') as stat_time, 'traffic_in_packets' as type, sum(total_in_packets) as packets from traffic_metrics_log where  __time >= $start_time and __time < $end_time group by DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/30)*30),'%Y-%m-%d %H:%i:%s')\n" +
                "union all\n" +
                "select DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/30)*30),'%Y-%m-%d %H:%i:%s') as stat_time, 'traffic_out_packets' as type, sum(total_out_packets) as packets from traffic_metrics_log where __time >= $start_time and __time < $end_time group by DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/30)*30),'%Y-%m-%d %H:%i:%s')");
        //Sessions
        list.add("select TIME_FORMAT(time_floor(__time,'PT30S'),'yyyy-MM-dd HH:mm:ss') as stat_time, 'new_conn_num' as type, sum(new_conn_num) as sessions from traffic_metrics_log where __time >= $start_time and __time < $end_time group by TIME_FORMAT(time_floor(__time,'PT30S'),'yyyy-MM-dd HH:mm:ss')");
        list.add("select DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/30)*30),'%Y-%m-%d %H:%i:%s') as stat_time, 'new_conn_num' as type, sum(new_conn_num) as sessions from traffic_metrics_log where __time >= $start_time and __time < $end_time group by DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/30)*30),'%Y-%m-%d %H:%i:%s')");
        //四、 Traffics-计数统计
        //1. 新建与活跃链接计数
        list.add("select  sum(new_conn_num) as new_conn_num, sum(established_conn_num) as established_conn_num from traffic_metrics_log  where   __time >=TIMESTAMP $start_time and __time <TIMESTAMP $end_time");
        list.add("select  sum(new_conn_num) as new_conn_num, sum(established_conn_num) as established_conn_num from traffic_metrics_log  where   __time >= $start_time and __time < $end_time");
        //2.新建与活跃链接计数趋势
        list.add("select TIME_FORMAT(time_floor(__time,'PT30S'),'yyyy-MM-dd HH:mm:ss') as stat_time, 'new_conn_num' as type, sum(new_conn_num) as sessions from traffic_metrics_log where  __time >=TIMESTAMP $start_time and __time < TIMESTAMP $end_time group by TIME_FORMAT(time_floor(__time,'PT30S'),'yyyy-MM-dd HH:mm:ss')\n" +
                "union all\n" +
                "select TIME_FORMAT(time_floor(__time,'PT30S'),'yyyy-MM-dd HH:mm:ss') as stat_time, 'established_conn_num' as type, sum(established_conn_num) as sessions from traffic_metrics_log where   __time >= TIMESTAMP $start_time and __time < TIMESTAMP $end_time  group by TIME_FORMAT(time_floor(__time,'PT30S'),'yyyy-MM-dd HH:mm:ss')");
        list.add("select DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/30)*30),'%Y-%m-%d %H:%i:%s') as stat_time, 'new_conn_num' as type, sum(new_conn_num) as sessions from traffic_metrics_log where  __time >= $start_time and __time < $end_time group by DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/30)*30),'%Y-%m-%d %H:%i:%s')\n" +
                "union all\n" +
                "select DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/30)*30),'%Y-%m-%d %H:%i:%s') as stat_time, 'established_conn_num' as type, sum(established_conn_num) as sessions from traffic_metrics_log where   __time >= $start_time and __time < $end_time  group by DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/30)*30),'%Y-%m-%d %H:%i:%s')");
        //3. 安全策略动作计数
        //Bytes
        list.add("select  sum(default_in_bytes+default_out_bytes) as default_bytes, sum(allow_in_bytes+allow_out_bytes) as allow_bytes, sum(deny_in_bytes+deny_out_bytes) as deny_bytes, sum(monitor_in_bytes+monitor_out_bytes) as monitor_bytes, sum(intercept_in_bytes+intercept_out_bytes) as intercept_bytes from traffic_metrics_log  where   __time >=TIMESTAMP $start_time and __time < TIMESTAMP $end_time");
        list.add("select  sum(default_in_bytes+default_out_bytes) as default_bytes, sum(allow_in_bytes+allow_out_bytes) as allow_bytes, sum(deny_in_bytes+deny_out_bytes) as deny_bytes, sum(monitor_in_bytes+monitor_out_bytes) as monitor_bytes, sum(intercept_in_bytes+intercept_out_bytes) as intercept_bytes from traffic_metrics_log  where   __time >= $start_time and __time < $end_time");
        //Packets
        list.add("select  sum(default_in_packets+default_out_packets) as default_packets, sum(allow_in_packets+allow_in_packets) as allow_packets, sum(deny_in_packets+deny_out_packets) as deny_packets, sum(monitor_in_packets+monitor_out_packets) as monitor_packets, sum(intercept_in_packets+intercept_out_packets) as intercept_packets from traffic_metrics_log  where   __time >=TIMESTAMP $start_time and __time <TIMESTAMP $end_time\n");
        list.add("select  sum(default_in_packets+default_out_packets) as default_packets, sum(allow_in_packets+allow_in_packets) as allow_packets, sum(deny_in_packets+deny_out_packets) as deny_packets, sum(monitor_in_packets+monitor_out_packets) as monitor_packets, sum(intercept_in_packets+intercept_out_packets) as intercept_packets from traffic_metrics_log  where   __time >= $start_time and __time < $end_time\n");
        //Sessions
        list.add("select sum(default_conn_num) as default_sessions, sum(allow_conn_num) as allow_sessions, sum(deny_conn_num) as deny_sessions, sum(monitor_conn_num) as monitor_sessions, sum(intercept_conn_num) as intercept_sessions from traffic_metrics_log  where   __time >=TIMESTAMP $start_time and __time <TIMESTAMP $end_time");
        list.add("select sum(default_conn_num) as default_sessions, sum(allow_conn_num) as allow_sessions, sum(deny_conn_num) as deny_sessions, sum(monitor_conn_num) as monitor_sessions, sum(intercept_conn_num) as intercept_sessions from traffic_metrics_log  where   __time >= $start_time and __time < $end_time");
        //4. 安全策略动作趋势
        //Bytes
        list.add("select TIME_FORMAT(time_floor(__time,'PT5M'),'yyyy-MM-dd HH:mm:ss') as stat_time, 'default_bytes' as type,  sum(default_in_bytes+default_out_bytes) as bytes from traffic_metrics_log where  __time >=TIMESTAMP $start_time and __time <TIMESTAMP $end_time group by TIME_FORMAT(time_floor(__time,'PT5M'),'yyyy-MM-dd HH:mm:ss')\n" +
                "union all\n" +
                "select TIME_FORMAT(time_floor(__time,'PT5M'),'yyyy-MM-dd HH:mm:ss') as stat_time, 'allow_bytes' as type,  sum(allow_in_bytes+allow_out_bytes) as bytes from traffic_metrics_log where  __time >=TIMESTAMP $start_time and __time <TIMESTAMP $end_time group by TIME_FORMAT(time_floor(__time,'PT5M'),'yyyy-MM-dd HH:mm:ss')\n" +
                "union all\n" +
                "select TIME_FORMAT(time_floor(__time,'PT5M'),'yyyy-MM-dd HH:mm:ss') as stat_time, 'deny_bytes' as type,  sum(deny_in_bytes+deny_out_bytes) as bytes from traffic_metrics_log where  __time >= TIMESTAMP $start_time and __time <TIMESTAMP $end_time group by TIME_FORMAT(time_floor(__time,'PT5M'),'yyyy-MM-dd HH:mm:ss') \n" +
                "union all\n" +
                "select TIME_FORMAT(time_floor(__time,'PT5M'),'yyyy-MM-dd HH:mm:ss') as stat_time, 'monitor_bytes' as type,  sum(monitor_in_bytes+monitor_out_bytes) as bytes from traffic_metrics_log where  __time >=TIMESTAMP $start_time and __time <TIMESTAMP $end_time group by TIME_FORMAT(time_floor(__time,'PT5M'),'yyyy-MM-dd HH:mm:ss')\n" +
                "union all\n" +
                "select TIME_FORMAT(time_floor(__time,'PT5M'),'yyyy-MM-dd HH:mm:ss') as stat_time, 'intercept_bytes' as type,  sum(intercept_in_bytes+intercept_out_bytes) as bytes from traffic_metrics_log where  __time >= $start_time and __time < $end_time group by TIME_FORMAT(time_floor(__time,'PT5M'),'yyyy-MM-dd HH:mm:ss')");
        list.add("select DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/300)*300),'%Y-%m-%d %H:%i:%s') as stat_time, 'default_bytes' as type,  sum(default_in_bytes+default_out_bytes) as bytes from traffic_metrics_log where  __time >= $start_time and __time < $end_time group by DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/300)*300),'%Y-%m-%d %H:%i:%s')\n" +
                "union all\n" +
                "select DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/300)*300),'%Y-%m-%d %H:%i:%s') as stat_time, 'allow_bytes' as type,  sum(allow_in_bytes+allow_out_bytes) as bytes from traffic_metrics_log where  __time >= $start_time and __time < $end_time group by DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/300)*300),'%Y-%m-%d %H:%i:%s')\n" +
                "union all\n" +
                "select DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/300)*300),'%Y-%m-%d %H:%i:%s') as stat_time, 'deny_bytes' as type,  sum(deny_in_bytes+deny_out_bytes) as bytes from traffic_metrics_log where  __time >= $start_time and __time < $end_time group by DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/300)*300),'%Y-%m-%d %H:%i:%s') \n" +
                "union all\n" +
                "select DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/300)*300),'%Y-%m-%d %H:%i:%s') as stat_time, 'monitor_bytes' as type,  sum(monitor_in_bytes+monitor_out_bytes) as bytes from traffic_metrics_log where  __time >= $start_time and __time < $end_time group by DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/300)*300),'%Y-%m-%d %H:%i:%s')\n" +
                "union all\n" +
                "select DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/300)*300),'%Y-%m-%d %H:%i:%s') as stat_time, 'intercept_bytes' as type,  sum(intercept_in_bytes+intercept_out_bytes) as bytes from traffic_metrics_log where  __time >= $start_time and __time < $end_time group by DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/300)*300),'%Y-%m-%d %H:%i:%s')");
        //packets
        list.add("select TIME_FORMAT(time_floor(__time,'PT5M'),'yyyy-MM-dd HH:mm:ss') as stat_time, 'default_packets' as type,  sum(default_in_packets+default_out_packets) as packets from traffic_metrics_log where  __time >=TIMESTAMP $start_time and __time <TIMESTAMP  $end_time group by TIME_FORMAT(time_floor(__time,'PT5M'),'yyyy-MM-dd HH:mm:ss')\n" +
                "union all\n" +
                "select TIME_FORMAT(time_floor(__time,'PT5M'),'yyyy-MM-dd HH:mm:ss') as stat_time, 'allow_packets' as type, sum(allow_in_packets+allow_out_packets) as packets from traffic_metrics_log where  __time >=TIMESTAMP  $start_time and __time <TIMESTAMP  $end_time group by TIME_FORMAT(time_floor(__time,'PT5M'),'yyyy-MM-dd HH:mm:ss')\n" +
                "union all\n" +
                "select TIME_FORMAT(time_floor(__time,'PT5M'),'yyyy-MM-dd HH:mm:ss') as stat_time, 'deny_packets' as type, sum(deny_in_packets+deny_out_packets) as packets from traffic_metrics_log where  __time >=TIMESTAMP  $start_time and __time <TIMESTAMP  $end_time group by TIME_FORMAT(time_floor(__time,'PT5M'),'yyyy-MM-dd HH:mm:ss') \n" +
                "union all\n" +
                "select TIME_FORMAT(time_floor(__time,'PT5M'),'yyyy-MM-dd HH:mm:ss') as stat_time, 'monitor_packets' as type, sum(monitor_in_packets+monitor_out_packets) as packets from traffic_metrics_log where  __time >=TIMESTAMP  $start_time and __time <TIMESTAMP  $end_time group by TIME_FORMAT(time_floor(__time,'PT5M'),'yyyy-MM-dd HH:mm:ss')\n" +
                "union all\n" +
                "select TIME_FORMAT(time_floor(__time,'PT5M'),'yyyy-MM-dd HH:mm:ss') as stat_time, 'intercept_packets' as type, sum(intercept_in_packets+intercept_out_packets) as packets from traffic_metrics_log where  __time >=TIMESTAMP  $start_time and __time <TIMESTAMP  $end_time group by TIME_FORMAT(time_floor(__time,'PT5M'),'yyyy-MM-dd HH:mm:ss')");
        list.add("select DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/300)*300),'%Y-%m-%d %H:%i:%s') as stat_time, 'default_packets' as type,  sum(default_in_packets+default_out_packets) as packets from traffic_metrics_log where  __time >= $start_time and __time < $end_time group by DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/300)*300),'%Y-%m-%d %H:%i:%s')\n" +
                "union all\n" +
                "select DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/300)*300),'%Y-%m-%d %H:%i:%s') as stat_time, 'allow_packets' as type, sum(allow_in_packets+allow_out_packets) as packets from traffic_metrics_log where  __time >= $start_time and __time < $end_time group by DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/300)*300),'%Y-%m-%d %H:%i:%s')\n" +
                "union all\n" +
                "select DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/300)*300),'%Y-%m-%d %H:%i:%s') as stat_time, 'deny_packets' as type, sum(deny_in_packets+deny_out_packets) as packets from traffic_metrics_log where  __time >= $start_time and __time < $end_time group by DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/300)*300),'%Y-%m-%d %H:%i:%s') \n" +
                "union all\n" +
                "select DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/300)*300),'%Y-%m-%d %H:%i:%s') as stat_time, 'monitor_packets' as type, sum(monitor_in_packets+monitor_out_packets) as packets from traffic_metrics_log where  __time >= $start_time and __time < $end_time group by DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/300)*300),'%Y-%m-%d %H:%i:%s')\n" +
                "union all\n" +
                "select DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/300)*300),'%Y-%m-%d %H:%i:%s') as stat_time, 'intercept_packets' as type, sum(intercept_in_packets+intercept_out_packets) as packets from traffic_metrics_log where  __time >= $start_time and __time < $end_time group by DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/300)*300),'%Y-%m-%d %H:%i:%s')");
        //sessions
        list.add("select TIME_FORMAT(time_floor(__time,'PT5M'),'yyyy-MM-dd HH:mm:ss') as stat_time, 'default_conn_num' as type, sum(default_conn_num) as sessions from traffic_metrics_log where  __time >=TIMESTAMP $start_time and __time <TIMESTAMP $end_time group by TIME_FORMAT(time_floor(__time,'PT5M'),'yyyy-MM-dd HH:mm:ss')\n" +
                "union all\n" +
                "select TIME_FORMAT(time_floor(__time,'PT5M'),'yyyy-MM-dd HH:mm:ss') as stat_time, 'allow_conn_num' as type, sum(allow_conn_num) as sessions from traffic_metrics_log where  __time >=TIMESTAMP $start_time and __time <TIMESTAMP $end_time group by TIME_FORMAT(time_floor(__time,'PT5M'),'yyyy-MM-dd HH:mm:ss')\n" +
                "union all\n" +
                "select TIME_FORMAT(time_floor(__time,'PT5M'),'yyyy-MM-dd HH:mm:ss') as stat_time, 'deny_conn_num' as type, sum(deny_conn_num) as sessions from traffic_metrics_log where  __time >=TIMESTAMP $start_time and __time <TIMESTAMP $end_time group by TIME_FORMAT(time_floor(__time,'PT5M'),'yyyy-MM-dd HH:mm:ss') \n" +
                "union all\n" +
                "select TIME_FORMAT(time_floor(__time,'PT5M'),'yyyy-MM-dd HH:mm:ss') as stat_time, 'monitor_conn_num' as type, sum(monitor_conn_num) as sessions from traffic_metrics_log where  __time >=TIMESTAMP $start_time and __time <TIMESTAMP $end_time group by TIME_FORMAT(time_floor(__time,'PT5M'),'yyyy-MM-dd HH:mm:ss')\n" +
                "union all\n" +
                "select TIME_FORMAT(time_floor(__time,'PT5M'),'yyyy-MM-dd HH:mm:ss') as stat_time, 'intercept_conn_num' as type, sum(intercept_conn_num) as sessions from traffic_metrics_log where  __time >=TIMESTAMP $start_time and __time <TIMESTAMP $end_time group by TIME_FORMAT(time_floor(__time,'PT5M'),'yyyy-MM-dd HH:mm:ss')");
        list.add("select DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/300)*300),'%Y-%m-%d %H:%i:%s') as stat_time, 'default_conn_num' as type, sum(default_conn_num) as sessions from traffic_metrics_log where  __time >= $start_time and __time < $end_time group by DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/300)*300),'%Y-%m-%d %H:%i:%s')\n" +
                "union all\n" +
                "select DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/300)*300),'%Y-%m-%d %H:%i:%s') as stat_time, 'allow_conn_num' as type, sum(allow_conn_num) as sessions from traffic_metrics_log where  __time >= $start_time and __time < $end_time group by DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/300)*300),'%Y-%m-%d %H:%i:%s')\n" +
                "union all\n" +
                "select DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/300)*300),'%Y-%m-%d %H:%i:%s') as stat_time, 'deny_conn_num' as type, sum(deny_conn_num) as sessions from traffic_metrics_log where  __time >= $start_time and __time < $end_time group by DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/300)*300),'%Y-%m-%d %H:%i:%s') \n" +
                "union all\n" +
                "select DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/300)*300),'%Y-%m-%d %H:%i:%s') as stat_time, 'monitor_conn_num' as type, sum(monitor_conn_num) as sessions from traffic_metrics_log where  __time >= $start_time and __time < $end_time group by DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/300)*300),'%Y-%m-%d %H:%i:%s')\n" +
                "union all\n" +
                "select DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/300)*300),'%Y-%m-%d %H:%i:%s') as stat_time, 'intercept_conn_num' as type, sum(intercept_conn_num) as sessions from traffic_metrics_log where  __time >= $start_time and __time < $end_time group by DATE_FORMAT(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(__time)/300)*300),'%Y-%m-%d %H:%i:%s')");
        //五. Traffics-TOPN统计
        //1. TopN 源IP
        list.add("select source as client_ip, sum(session_num) as sessions, sum(c2s_byte_num) as sent_bytes, sum(s2c_byte_num) as received_bytes, sum(c2s_byte_num + s2c_byte_num) as bytes,  sum(c2s_pkt_num) as sent_packets ,sum(s2c_pkt_num) as received_packets, sum(c2s_pkt_num+s2c_pkt_num) as packets from top_internal_host_log where  __time >=TIMESTAMP $start_time and __time <TIMESTAMP $end_time and order_by=source group by source order by source desc limit 100");
        list.add("select source as client_ip, sum(session_num) as sessions, sum(c2s_byte_num) as sent_bytes, sum(s2c_byte_num) as received_bytes, sum(c2s_byte_num + s2c_byte_num) as bytes,  sum(c2s_pkt_num) as sent_packets ,sum(s2c_pkt_num) as received_packets, sum(c2s_pkt_num+s2c_pkt_num) as packets from top_internal_host_log where  __time >= $start_time and __time < $end_time and order_by=source group by source order by source desc limit 100");
        //2. TopN 目的IP
        list.add("select destination as server_ip, sum(session_num) as sessions, sum(c2s_byte_num) as sent_bytes, sum(s2c_byte_num) as received_bytes, sum(c2s_byte_num + s2c_byte_num) as bytes,  sum(c2s_pkt_num) as sent_packets ,sum(s2c_pkt_num) as received_packets, sum(c2s_pkt_num+s2c_pkt_num) as packets from top_external_host_log  where  __time >=TIMESTAMP $start_time and __time <TIMESTAMP $end_time and order_by=destination group by destination order by destination desc limit 100");
        list.add("select destination as server_ip, sum(session_num) as sessions, sum(c2s_byte_num) as sent_bytes, sum(s2c_byte_num) as received_bytes, sum(c2s_byte_num + s2c_byte_num) as bytes,  sum(c2s_pkt_num) as sent_packets ,sum(s2c_pkt_num) as received_packets, sum(c2s_pkt_num+s2c_pkt_num) as packets from top_external_host_log  where  __time >= $start_time and __time < $end_time and order_by=destination group by destination order by destination desc limit 100");
        //3. TopN 域名
        list.add("select domain, sum(session_num) as sessions, sum(c2s_byte_num) as sent_bytes, sum(s2c_byte_num) as received_bytes, sum(c2s_byte_num + s2c_byte_num) as bytes,  sum(c2s_pkt_num) as sent_packets ,sum(s2c_pkt_num) as received_packets, sum(c2s_pkt_num+s2c_pkt_num) as packets from top_website_domain_log where  __time >=TIMESTAMP $start_time and __time <TIMESTAMP $end_time  and order_by=domain group by domain order by domain desc limit 200");
        list.add("select domain, sum(session_num) as sessions, sum(c2s_byte_num) as sent_bytes, sum(s2c_byte_num) as received_bytes, sum(c2s_byte_num + s2c_byte_num) as bytes,  sum(c2s_pkt_num) as sent_packets ,sum(s2c_pkt_num) as received_packets, sum(c2s_pkt_num+s2c_pkt_num) as packets from top_website_domain_log where  __time >= $start_time and __time < $end_time  and order_by=domain group by domain order by domain desc limit 200");
        //4.TopN 用户
        list.add("select subscriber_id, sum(session_num) as sessions, sum(c2s_byte_num) as sent_bytes, sum(s2c_byte_num) as received_bytes, sum(c2s_byte_num + s2c_byte_num) as bytes,  sum(c2s_pkt_num) as sent_packets ,sum(s2c_pkt_num) as received_packets, sum(c2s_pkt_num+s2c_pkt_num) as packets from top_user_log where  __time >=TIMESTAMP $start_time and __time <TIMESTAMP $end_time  and order_by=subscriber_id group by subscriber_id order by subscriber_id desc limit 200");
        list.add("select subscriber_id, sum(session_num) as sessions, sum(c2s_byte_num) as sent_bytes, sum(s2c_byte_num) as received_bytes, sum(c2s_byte_num + s2c_byte_num) as bytes,  sum(c2s_pkt_num) as sent_packets ,sum(s2c_pkt_num) as received_packets, sum(c2s_pkt_num+s2c_pkt_num) as packets from top_user_log where  __time >= $start_time and __time < $end_time  and order_by=subscriber_id group by subscriber_id order by subscriber_id desc limit 200");
        //六、命中URL统计
        list.add("select url,sum(session_num) as hits  from top_urls_log where __time >=TIMESTAMP $start_time and __time <TIMESTAMP $end_time  group by url order by hits desc limit 100");
        list.add("select url,sum(session_num) as hits  from top_urls_log where __time >= $start_time and __time < $end_time  group by url order by hits desc limit 100");

        //国家客户端数量
        list.add("SELECT country, APPROX_COUNT_DISTINCT_DS_HLL(ip_object) as num FROM source_country_ip_num_log group by country order by num desc limit 200");
        //域名推荐
        list.add("SELECT domain, protocol_type, ip_num, session_num  FROM top_domain_recommend_daily_log where __time = now()");
        //推荐URL
        list.add("SELECT url, content_length, ip_num, session_num  FROM top_website_urls_daily_log where __time = now()");
        //劫持客户端数量
        list.add("SELECT policy_id, APPROX_COUNT_DISTINCT_DS_HLL(isp) as num  FROM proxy_event_hits_log where  __time >= $start_time and __time < $end_time and policy_id=0 group by policy_id");
        //劫持客户端IP地域分布
        list.add("select ip, IP_TO_CITY(ip) as location, IP_TO_GEO(ip) as geo from (SELECT policy_id, arrayJoin(groupUniqArrayMerge(ip_list)) as ip FROM proxy_ip_info where  stat_time >= $start_time and stat_time < $end_time and policy_id=0 group by policy_id)");

        return list;
    }


    /**
     * 查询clickhouse数据库
     *
     * @param query
     * @return
     */
    private List queryCKDynamic(String query) {
        StringBuilder urlBuilder = new StringBuilder("http://")
                .append(clickHouseHttpSource.getUrl()).append("/?");
        StringBuilder queryparamBuilder = new StringBuilder("user=")
                .append(clickHouseHttpSource.getRealTimeAccount().getUsername()).append("&")
                .append("password=").append(clickHouseHttpSource.getRealTimeAccount().getPassword()).append("&")
                .append("database=")
                .append(clickHouseHttpSource.getDbName()).append("&")
                .append("query=").append(query)
                .append(" FORMAT JSON;");

        log.info("Query Session and Events Query : {}", urlBuilder.toString() + Encodes.urlDecode(queryparamBuilder.toString()));
        List<NameValuePair> values = URLEncodedUtils.parse(queryparamBuilder.toString(), Charset.forName("UTF-8"));
        String s = urlBuilder.toString() + URLEncodedUtils.format(values, "utf-8");
        Map<String, String> resultMap = httpClientService.httpGet(s, httpConfig.getClickHouseRealTimeResponseTimeOut());
        List result = new ArrayList<>();
        if (resultMap.get("status").equals(String.valueOf(HttpStatus.SC_OK))) {
            Map<String, Object> maps = (Map<String, Object>) JsonMapper.fromJsonString(resultMap.get("result"), Map.class);
            List data = (List) maps.get("data");
            Iterator iterator = data.iterator();
            while (iterator.hasNext()) {
                Map next = (Map) iterator.next();
                result.add(String.valueOf(next.get("name")));
            }
            return result;
        }

        return null;
    }


    /**
     * 查询druid数据库
     *
     * @param query
     * @return
     */
    private List queryDruidDynamic(String query) {
        //headers
        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);
        //body
        Map<String, Object> params = Maps.newHashMap();
        params.put("query", query);
        int socketTimeOut = httpConfig.getDruidResponseTimeOut();
        Map<String, String> resultMap = httpClientService.httpPost("http://" + druidIoProperties.getUrl(), JsonMapper.toJsonString(params), socketTimeOut);
        if (resultMap.get("status").equals(String.valueOf(HttpStatus.SC_OK))) {
            List<String> list = new ArrayList<>();
            String result = resultMap.get("result");
            List body = (List) Json.parseJson(result);
            Iterator iterator = body.iterator();
            while (iterator.hasNext()) {
                Map next = (Map) iterator.next();
                String name = String.valueOf(next.get("name"));
                list.add(name);
            }
            return list;
        }
        return null;
    }


    private void checkDruidSchema(String path) {
        Map<String, JsonProperties> druidSchemaInfo = getSchemaInfo(path);
        Set<String> name = druidSchemaInfo.keySet();
        Iterator<String> iterator = name.iterator();
        while (iterator.hasNext()) {//遍历表,以Avro为基础
            String tableName = iterator.next();
            Map resultMap = (Map) com.zdjizhi.utils.JsonMapper.fromJsonString(String.valueOf(druidSchemaInfo.get(tableName)), Map.class);
            if (resultMap == null) {
                throw new BusinessException(ResultStatusEnum.FAIL.getCode(), "schema未注册此表: " + tableName, null);
            }
            //查数据库
            List resultSql = queryDruidDynamic("SELECT COLUMN_NAME as name FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = '" + tableName + "'");

            List resultAvro = new ArrayList();

            //schema为基础
            List<Map> fields = (List<Map>) resultMap.get("fields");//遍历列
            Iterator<Map> fieldsAvro = fields.iterator();
            while (fieldsAvro.hasNext()) {
                Map next = fieldsAvro.next();
                resultAvro.add(String.valueOf(next.get("name")));
                boolean b = resultSql.contains(next.get("name"));
                if (!b) {
                    String str = String.format("%s.avro中注册多余:%s", tableName, next.get("name"));
                    errorDate.add(str);
                }
            }

            //Schema为基础
            Iterator iteratorSql = resultSql.iterator();
            while (iteratorSql.hasNext()) {
                String next = String.valueOf(iteratorSql.next());
                boolean b = resultAvro.contains(next);
                if (!b) {
                    String str = String.format("%s.avro中注册缺少:%s", tableName, next);
                    errorDate.add(str);
                }
            }
        }
    }


    private void checkCKSchema(String path) {
        Map<String, JsonProperties> ckSchemaInfo = getSchemaInfo(path);
        Set<String> name = ckSchemaInfo.keySet();
        Iterator<String> iterator = name.iterator();
        while (iterator.hasNext()) {//遍历表,以Avro为基础
            String tableName = iterator.next();
            Map resultMap = (Map) com.zdjizhi.utils.JsonMapper.fromJsonString(String.valueOf(ckSchemaInfo.get(tableName)), Map.class);
            if (resultMap == null) {
                throw new BusinessException(ResultStatusEnum.FAIL.getCode(), "schema未注册此表: " + tableName, null);
            }
            //查数据库
            List resultSql = queryCKDynamic("describe " + tableName);
            if (StringUtil.isEmpty(resultSql)) {
                String str = String.format("avro中注册多余:%s.avsc", tableName);
                errorDate.add(str);
                continue;
            }

            List resultAvro = new ArrayList();

            //avro为基础
            List<Map> fields = (List<Map>) resultMap.get("fields");//遍历列
            Iterator<Map> fieldsAvro = fields.iterator();
            while (fieldsAvro.hasNext()) {
                Map next = fieldsAvro.next();
                resultAvro.add(String.valueOf(next.get("name")));
                boolean b = resultSql.contains(next.get("name"));
                if (!b) {
                    String str = String.format("%s.avro中注册多余:%s", tableName, next.get("name"));
                    errorDate.add(str);
                }
            }

            //db为基础
            Iterator iteratorSql = resultSql.iterator();
            while (iteratorSql.hasNext()) {
                String next = String.valueOf(iteratorSql.next());
                boolean b = resultAvro.contains(next);
                if (!b) {
                    String str = String.format("%s.avro中注册缺少:%s", tableName, next);
                    errorDate.add(str);
                }
            }
        }
    }


    /**
     * 获取avro文件
     *
     * @param path
     * @return
     */
    private Map<String, JsonProperties> getSchemaInfo(String path) {
        Map<String, JsonProperties> schema = new HashMap<>();
        String pathRoot = null;
        try {
            pathRoot = new File("").getCanonicalPath();
            File file = new File(pathRoot + path);
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (!f.getName().toLowerCase().endsWith(".avsc")) {
                        continue;
                    }
                    try {
                        Schema ckSchema = new Schema.Parser().parse(new File(f.getPath()));
                        schema.put(ckSchema.getName(), ckSchema);
                    } catch (Exception ex) {
                        log.error("遍历" + path + "路径下文件失败: " + f.getName(), ex);
                        String str = String.format("%s读取文件失败:%s", path, f.getName());
                        errorDate.add(str);
                    }
                }
            }
        } catch (Exception e) {
            log.error("获取 项目路径/{}失败", path);
            String str = String.format("获取项目路径%s失败", path);
            errorDate.add(str);
        }
        return schema;
    }


    /**
     * 核对avro文件
     *
     * @param path 路径
     */
    private void checkSchema(String path) {
        Map<String, JsonProperties> ckeckSchemaInfo = getSchemaInfo(path);
        Set<String> name = ckeckSchemaInfo.keySet();
        Iterator<String> iterator = name.iterator();
        while (iterator.hasNext()) {//遍历表
            String tableName = iterator.next();
            Map resultMap = (Map) com.zdjizhi.utils.JsonMapper.fromJsonString(String.valueOf(ckeckSchemaInfo.get(tableName)), Map.class);
            if (resultMap == null) {
                throw new BusinessException(ResultStatusEnum.FAIL.getCode(), "schema未注册此表: " + tableName, null);
            }
            List<Map> fields = (List<Map>) resultMap.get("fields");//遍历列
            Iterator<Map> fieldsIterator = fields.iterator();
            Map mapDoc = null;
            while (fieldsIterator.hasNext()) {
                Map next = fieldsIterator.next();
                try {
                    mapDoc = (Map) com.zdjizhi.utils.JsonMapper.fromJsonString(String.valueOf(next.get("doc")), Map.class);
                } catch (Exception ex) {
                    log.error("该字段的 doc 为非JSON字符串，不需类型转换");
                    String str = String.format("%s.avro中doc非json格式字段%s", tableName, next.get("name"));
                    errorDate.add(str);
                }
                if (mapDoc != null) {
                    next.put("doc", mapDoc);
                }
                if (StringUtil.isEmpty(mapDoc) && !StringUtil.isEmpty(next.get("doc"))) {
                    log.error("该字段的 doc 为非JSON字符串，不需类型转换");
                    String str = String.format("%s.avro中doc非json格式字段%s", tableName, next.get("name"));
                    errorDate.add(str);
                }
            }
        }
    }
}
