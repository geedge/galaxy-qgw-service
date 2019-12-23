package com.mesalab.qgw.service.impl;


import com.google.common.collect.Maps;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.configuration.ClickHouseProperties;
import com.mesalab.common.configuration.DruidIoProperties;
import com.mesalab.common.enums.BaseResultEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.common.utils.SpringContextUtil;
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
    private HttpClientService httpClientService = (HttpClientService) SpringContextUtil.getBean("httpClientService");

    @Autowired
    private ClickHouseProperties clickHouseProperties;
    @Autowired
    public APIServiceImpl apiServiceImpl;
    @Autowired
    public DruidIoProperties druidIoProperties;
    @Autowired
    public ClickHouseHttpSource clickHouseHttpSource;

    private static String errorDate;



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

    public BaseResult runSchema(){
        errorDate = "";
        checkCKSchema("/config/avro/clickhouse");
        checkDruidSchema("/config/avro/druid");
        checkSchema("/config/avro/clickhouse");
        checkSchema("/config/avro/druid");
        String message = "".equals(errorDate) ? "ok" : "error";
        return BaseResultGenerator.success(message, errorDate);
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


    public List queryCKDynamic(String query){
        StringBuilder urlBuilder = new StringBuilder("http://")
                .append(clickHouseHttpSource.getUrl()).append("/?");
        StringBuilder queryparamBuilder = new StringBuilder("user=")
                .append(clickHouseHttpSource.getUsername()).append("&")
                .append("password=").append(clickHouseHttpSource.getPassword()).append("&")
                .append("database=")
                .append(clickHouseHttpSource.getDbname()).append("&")
                .append("query=").append(query)
                .append(" FORMAT JSON;");

        log.info("Query Session and Events Query : {}" , urlBuilder.toString() + Encodes.urlDecode(queryparamBuilder.toString()));
        List<NameValuePair> values = URLEncodedUtils.parse(queryparamBuilder.toString(), Charset.forName("UTF-8"));
        String s = urlBuilder.toString() + URLEncodedUtils.format(values, "utf-8");
        Map<String, String> resultMap = httpClientService.httpGet(s);
        List result = new ArrayList<>();
        if(resultMap.get("status").equals(String.valueOf(HttpStatus.SC_OK))){
            Map<String, Object> maps =(Map<String, Object>) JsonMapper.fromJsonString(resultMap.get("result"),  Map.class);
            List data = (List)maps.get("data");
            Iterator iterator = data.iterator();
            while (iterator.hasNext()){
                Map next = (Map)iterator.next();
                result.add(String.valueOf(next.get("name")));
            }
            return result;
        }

        return null;
    }


    public List queryDruidDynamic(String query){
        //headers
        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);
        //body
        Map<String, Object> params = Maps.newHashMap();
        params.put("query", query);
        Map<String, String> resultMap = httpClientService.httpPost("http://" + druidIoProperties.getUrl(), JsonMapper.toJsonString(params));
        if(resultMap.get("status").equals(String.valueOf(HttpStatus.SC_OK))){
            List<String> list = new ArrayList<>();
            String result = resultMap.get("result");
            List body = (List)Json.parseJson(result);
            Iterator iterator = body.iterator();
            while (iterator.hasNext()){
                Map next = (Map)iterator.next();
                String name = String.valueOf(next.get("name"));
                list.add(name);
            }
            return list;
        }
        return null;
    }


    public void checkDruidSchema(String path){
        Map<String, JsonProperties> druidSchemaInfo = getSchemaInfo(path);
        Set<String> name = druidSchemaInfo.keySet();
        Iterator<String> iterator = name.iterator();
        while (iterator.hasNext()){//遍历表,以Avro为基础
            String tableName = iterator.next();
            Map resultMap = (Map) com.zdjizhi.utils.JsonMapper.fromJsonString(String.valueOf(druidSchemaInfo.get(tableName)), Map.class);
            if (resultMap == null) {
                throw new BusinessException(BaseResultEnum.FAIL.getCode(), "schema未注册此表: " + tableName, null);
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
                if(!b){
                    String str = String.format("%s.avro中注册多余:%s;", tableName, next.get("name"));
                    errorDate += str;
                }
            }

            //Schema为基础
            Iterator iteratorSql = resultSql.iterator();
            while (iteratorSql.hasNext()){
                String next = String.valueOf(iteratorSql.next());
                boolean b = resultAvro.contains(next);
                if(!b){
                    String str = String.format("%s.avro中注册缺少:%s;", tableName, next);
                    errorDate += str;
                }
            }
        }
    }


    public void checkCKSchema(String path){
        Map<String, JsonProperties> ckSchemaInfo = getSchemaInfo(path);
        Set<String> name = ckSchemaInfo.keySet();
        Iterator<String> iterator = name.iterator();
        while (iterator.hasNext()){//遍历表,以Avro为基础
            String tableName = iterator.next();
            Map resultMap = (Map) com.zdjizhi.utils.JsonMapper.fromJsonString(String.valueOf(ckSchemaInfo.get(tableName)), Map.class);
            if (resultMap == null) {
                throw new BusinessException(BaseResultEnum.FAIL.getCode(), "schema未注册此表: " + tableName, null);
            }
            //查数据库
            List resultSql = queryCKDynamic("describe " + tableName);

            List resultAvro = new ArrayList();

            //schema为基础
            List<Map> fields = (List<Map>) resultMap.get("fields");//遍历列
            Iterator<Map> fieldsAvro = fields.iterator();
            while (fieldsAvro.hasNext()) {
                Map next = fieldsAvro.next();
                resultAvro.add(String.valueOf(next.get("name")));
                boolean b = resultSql.contains(next.get("name"));
                if(!b){
                    String str = String.format("%s.avro中注册多余:%s;", tableName, next.get("name"));
                    errorDate += str;
                }
            }

            //Schema为基础
            Iterator iteratorSql = resultSql.iterator();
            while (iteratorSql.hasNext()){
                String next = String.valueOf(iteratorSql.next());
                boolean b = resultAvro.contains(next);
                if(!b){
                    String str = String.format("%s.avro中注册缺少:%s;", tableName, next);
                    errorDate += str;
                }
            }
        }
    }


    /**
     * 获取avro文件
     * @param path
     * @return
     */
    public static Map<String, JsonProperties> getSchemaInfo(String path){
        Map<String, JsonProperties> schema = new HashMap<>();
        String pathRoot= null;
        try {
            pathRoot = new File("").getCanonicalPath();
            File file = new File(pathRoot + path);
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    if(!f.getName().toLowerCase().endsWith(".avsc")){
                        continue;
                    }
                    try {
                        Schema ckSchema = new Schema.Parser().parse(new File(f.getPath()));
                        schema.put(ckSchema.getName(), ckSchema);
                    }catch (Exception ex){
                        log.error("遍历" + path + "路径下文件失败: " + f.getName(), ex);
                        String str = String.format("遍历%s路径下文件失败%s;", path, f.getName());
                        errorDate += str;
                    }
                }
            }
        } catch (Exception e) {
            log.error("获取 项目路径/{}失败", path);
            String str = String.format("获取项目路径%s失败;", path);
            errorDate += str;
        }
        return schema;
    }


    /**
     * 核对avro文件
     *
     * @param path 路径
     */
    public void checkSchema(String path) {
        Map<String, JsonProperties> ckeckSchemaInfo = getSchemaInfo(path);
        Set<String> name = ckeckSchemaInfo.keySet();
        Iterator<String> iterator = name.iterator();
        while (iterator.hasNext()) {//遍历表
            String tableName = iterator.next();
            Map resultMap = (Map) com.zdjizhi.utils.JsonMapper.fromJsonString(String.valueOf(ckeckSchemaInfo.get(tableName)), Map.class);
            if (resultMap == null) {
                throw new BusinessException(BaseResultEnum.FAIL.getCode(), "schema未注册此表: " + tableName, null);
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
                    String str = String.format("%s.avro中%s字段doc非json格式;", tableName, next.get("name"));
                    errorDate += str;
                }
                if (mapDoc != null) {
                    next.put("doc", mapDoc);
                }
                if (StringUtil.isEmpty(mapDoc) && !StringUtil.isEmpty(next.get("doc"))) {
                    log.error("该字段的 doc 为非JSON字符串，不需类型转换");
                    String str = String.format("%s.avro中%s字段doc非json格式;", tableName, next.get("name"));
                    errorDate += str;
                }
            }
        }
    }
}
