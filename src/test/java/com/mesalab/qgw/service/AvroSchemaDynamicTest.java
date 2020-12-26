package com.mesalab.qgw.service;

import cn.hutool.core.util.URLUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.collect.Maps;
import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.configuration.ClickHouseProperties;
import com.mesalab.common.configuration.DruidIoProperties;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.qgw.model.api.ClickHouseHttpSource;
import com.mesalab.qgw.service.impl.ApiServiceImpl;
import com.zdjizhi.utils.Encodes;
import org.apache.avro.JsonProperties;
import org.apache.avro.Schema;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.File;
import java.nio.charset.Charset;
import java.util.*;

@EnableAutoConfiguration
public class AvroSchemaDynamicTest extends GalaxyQGWApplicationTests {
    private static final Log log = LogFactory.get();
    private static final String CK_SCHEMA_DIR = File.separator + "config"
            + File.separator + "avro" +  File.separator + "clickhouse";
    private static final String DRUID_SCHEMA_DIR = File.separator + "config"
            + File.separator + "avro" +  File.separator + "druid";
    @Autowired
    public TestRestTemplate restTemplate;
    @Autowired
    public ApiServiceImpl apiServiceImpl;
    @Autowired
    public ClickHouseProperties clickHouseProperties;
    @Autowired
    public DruidIoProperties druidIoProperties;
    @Autowired
    public ClickHouseHttpSource clickHouseHttpSource;


    @Test
    public void testCKSchema(){
        checkCKSchema(CK_SCHEMA_DIR);
        log.info("clickhouse schema dynamic 检测完成");
    }


    @Test
    public void testDruidSchema(){
        checkDruidSchema(DRUID_SCHEMA_DIR);
        log.info("druid schema dynamic 检测完成");
    }

    public List queryCKDynamic(String query){
        String queryURL = URLUtil.normalize(clickHouseHttpSource.getUrl() + "/?");
        StringBuilder queryparamBuilder = new StringBuilder("user=")
                .append(clickHouseHttpSource.getRealTimeAccount().getUsername()).append("&")
                .append("password=").append(clickHouseHttpSource.getRealTimeAccount().getUserKey()).append("&")
                .append("database=").append(clickHouseHttpSource.getDbName()).append("&")
                .append("query=").append(query)
                .append(" FORMAT JSON;");

        log.info("Query Session and Events Query : {}" , queryURL + Encodes.urlDecode(queryparamBuilder.toString()));
        List<NameValuePair> values = URLEncodedUtils.parse(queryparamBuilder.toString(), Charset.forName("UTF-8"));
        String s =queryURL + URLEncodedUtils.format(values, "utf-8");
        ResponseEntity<Map> forEntity = restTemplate.getForEntity(s, Map.class);
        List result = new ArrayList<>();
        if(forEntity.getStatusCodeValue() == 200){
            Map body = forEntity.getBody();
            List data = (List)body.get("data");
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
        HttpEntity<Map> requestEntity = new HttpEntity<Map>(params, requestHeaders);
        String queryURL = URLUtil.normalize(druidIoProperties.getUrl());
        ResponseEntity<List> result = restTemplate.postForEntity(queryURL, requestEntity, List.class);
        if(result.getStatusCodeValue() == 200){
            List<String> list = new ArrayList<>();
            List body = result.getBody();
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
                if(!b){
                    throw new RuntimeException(tableName + " avro中注册多余 " + next.get("name"));
                }
            }

            //Schema为基础
            Iterator iteratorSql = resultSql.iterator();
            while (iteratorSql.hasNext()){
                String next = String.valueOf(iteratorSql.next());
                boolean b = resultAvro.contains(next);
                if(!b){
                    throw new RuntimeException(tableName + " avro中缺少注册 " + next);
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
                throw new BusinessException(ResultStatusEnum.FAIL.getCode(), "schema未注册此表: " + tableName, null);
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
                    throw new RuntimeException(tableName + " avro中注册多余 " + next.get("name"));
                }
            }

            //Schema为基础
            Iterator iteratorSql = resultSql.iterator();
            while (iteratorSql.hasNext()){
                String next = String.valueOf(iteratorSql.next());
                boolean b = resultAvro.contains(next);
                if(!b){
                    throw new RuntimeException(tableName + " avro中缺少注册 " + next);
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
        Map<String, JsonProperties> cKSchema = new HashMap<>();
        String pathRoot= null;
        try {
            pathRoot = new File("").getCanonicalPath();
            File file = new File(pathRoot + path);
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    if(!StringUtils.lowerCase(f.getName()).endsWith(".avsc")){
                        continue;
                    }
                    try {
                        Schema ckSchema = new Schema.Parser().parse(new File(f.getPath()));
                        cKSchema.put(ckSchema.getName(), ckSchema);
                    }catch (Exception ex){
                        log.error("遍历" + path + "路径下文件失败: " + f.getName(), ex);
                        throw new RuntimeException(String.format("遍历%s 路径下文件%s失败: %s", path, f.getName()), ex);
                    }
                }
            }
        } catch (Exception e) {
            log.error("获取 项目路径下文件失败", path);
            throw new RuntimeException(String.format("遍历%s 路径下文件失败", path));
        }
        return cKSchema;
    }

    @Before
    public void testBefore() {
        log.info("=================================================SCHEMA TEST BEGIN=================================================");
    }
    @After
    public void testAfter() {
        log.info("=================================================SCHEMA TEST END=================================================");
    }

}
