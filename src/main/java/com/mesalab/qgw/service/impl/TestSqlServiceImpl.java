package com.mesalab.qgw.service.impl;


import com.mesalab.common.base.BaseResult;
import com.mesalab.common.configuration.ClickHouseProperties;
import com.mesalab.common.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.*;

@Slf4j
@Service
public class TestSqlServiceImpl {
    private HttpClientService httpClientService = (HttpClientService) SpringContextUtil.getBean("httpClientService");

    @Autowired
    private ClickHouseProperties clickHouseProperties;
    public BaseResult runSql(){

        List list = ckSql();
        Map<String, String> resultTemp = null;
        BaseResult<Object> result = new BaseResult<>();
        List<Object> data = new ArrayList<>();
        int countSuccess = 0;
        SimpleDateFormat format1 = new SimpleDateFormat("yyyy-MM-dd HH:00:00");
        SimpleDateFormat format2 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date date = new Date(System.currentTimeMillis());
        for (int i = 0; i < list.size(); i++){
            String sql = String.valueOf(list.get(i));
            try {
                sql = sql.replaceAll("\\$start_time", "'" + format1.format(date) + "'");
                sql = sql.replaceAll("\\$end_time", "'" + format2.format(date) + "'");
                sql = URLEncoder.encode(sql, "utf-8").replaceAll("\\+","%20");
            } catch (Exception e) {
                e.printStackTrace();
                log.error("测试用例sql编码错误", e);
            }
            resultTemp = httpClientService.httpGet("http://127.0.0.1:9999/?query=" + sql);
            if(resultTemp.get("status").equals(String.valueOf(HttpStatus.SC_OK))){
                resultTemp.remove("result");
                countSuccess++;
            }else {
                try {
                    resultTemp.put("query", URLDecoder.decode(sql, "UTF-8"));
                } catch (Exception e) {
                    e.printStackTrace();
                    log.error("sql查询结果处理异常", e);
                }
            }
            data.add(resultTemp);
        }
        result.setMessage(String.format("total:%s,success:%s",list.size(),countSuccess));
        result.setData(data);
        return result;
    }


    public List ckSql(){
        List<String> list = new ArrayList<>();
        list.add("select toDateTime(common_recv_time) as common_recv_time,common_subscriber_id, common_address_type, common_l4_protocol, common_client_ip, common_server_ip, common_server_port, ssl_sni, ssl_version from security_event_log where common_recv_time >= toDateTime($start_time) and common_recv_time< toDateTime($end_time) and common_client_ip like '49.7%' order by common_recv_time desc limit 0,20");
        list.add("select FROM_UNIXTIME(common_recv_time) as common_recv_time,common_subscriber_id, common_address_type, common_l4_protocol, common_client_ip, common_server_ip, common_server_port, ssl_sni, ssl_version from security_event_log where common_recv_time >= UNIX_TIMESTAMP($start_time) and common_recv_time< UNIX_TIMESTAMP($end_time) and common_client_ip like '49.7%' order by common_recv_time desc limit 0,20");
        list.add("select toDateTime(common_recv_time) as common_recv_time,common_subscriber_id, common_address_type, common_l4_protocol, common_client_ip, common_server_ip, common_server_port, http_host,http_url,http_user_agent from proxy_event_log where common_recv_time >= toDateTime($start_time) and common_recv_time< toDateTime($end_time) and http_host like '%joy.cn%' order by common_recv_time desc limit 0,20");
        list.add("select FROM_UNIXTIME(common_recv_time) as common_recv_time,common_subscriber_id, common_address_type, common_l4_protocol, common_client_ip, common_server_ip, common_server_port, http_host,http_url,http_user_agent from proxy_event_log where common_recv_time >= UNIX_TIMESTAMP($start_time) and common_recv_time< UNIX_TIMESTAMP($end_time) and http_host like '%joy.cn%' order by common_recv_time desc limit 0,20");
        list.add("select toDateTime(common_recv_time) as common_recv_time, common_address_type, common_l4_protocol, common_client_ip, common_server_ip, common_server_port, radius_framed_ip, radius_account from radius_record_log where common_recv_time >= toDateTime($start_time) and common_recv_time< toDateTime($start_time) and radius_account='T1yRd' order by common_recv_time desc limit 0,20");
        list.add("select FROM_UNIXTIME(common_recv_time) as common_recv_time,common_subscriber_id, common_address_type, common_l4_protocol, common_client_ip, common_server_ip, common_server_port, http_host,http_url,http_user_agent from proxy_event_log where common_recv_time >= UNIX_TIMESTAMP($start_time) and common_recv_time< UNIX_TIMESTAMP($end_time) and http_host like '%joy.cn%' order by common_recv_time desc limit 0,20");
        list.add("select toDateTime(common_recv_time) as common_recv_time, common_address_type, common_l4_protocol, common_client_ip, common_server_ip, common_server_port, radius_framed_ip, radius_account from radius_record_log where common_recv_time >= toDateTime($start_time) and common_recv_time< toDateTime($start_time) and radius_account='T1yRd' order by common_recv_time desc limit 0,20");
        list.add("select FROM_UNIXTIME(common_recv_time) as common_recv_time, common_address_type, common_l4_protocol, common_client_ip, common_server_ip, common_server_port, radius_framed_ip, radius_account from radius_record_log where common_recv_time >= UNIX_TIMESTAMP($start_time) and common_recv_time< UNIX_TIMESTAMP($end_time) and radius_account='T1yRd' order by common_recv_time desc limit 0,20");

        list.add("select count(1) from connection_record_log");
        list.add("select count(*) from connection_record_log");
        list.add("select * from "+clickHouseProperties.getDbname() +".connection_record_log limit 1");
        return list;
    }
}
