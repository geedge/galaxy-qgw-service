package com.mesalab.qgw.service;

import com.google.common.collect.Maps;
import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.SchemaBase;
import com.mesalab.common.configuration.ClickHouseProperties;
import com.mesalab.common.configuration.DruidIoProperties;
import com.mesalab.common.enums.BaseResultEnum;
import com.mesalab.common.enums.DBTypeEnum;
import com.mesalab.common.enums.QueryFormatEnum;
import com.mesalab.common.enums.QueryOptionEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.common.utils.SpringContextUtil;
import com.mesalab.qgw.dialect.ClickhouseDialect;
import com.mesalab.qgw.dialect.Dialect;
import com.mesalab.qgw.dialect.DruidDialect;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.ClickHouseHttpSource;
import com.mesalab.qgw.service.impl.APIServiceImpl;
import com.mesalab.qgw.service.impl.HttpClientService;
import com.zdjizhi.utils.Encodes;
import com.zdjizhi.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.statement.DescribeStatement;
import net.sf.jsqlparser.statement.ShowStatement;
import org.apache.avro.JsonProperties;
import org.apache.avro.Schema;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.json.GsonTester;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.File;
import java.nio.charset.Charset;
import java.util.*;

@Slf4j
@EnableAutoConfiguration
public class AvroSchemaDynamicTest extends GalaxyQGWApplicationTests {

    @Autowired
    public TestRestTemplate restTemplate;
    @Autowired
    public APIServiceImpl apiServiceImpl;
    @Autowired
    public ClickHouseProperties clickHouseProperties;
    @Autowired
    public DruidIoProperties druidIoProperties;
    @Autowired
    public ClickHouseHttpSource clickHouseHttpSource;


    @Test
    public void testCKSchema(){
        checkCKSchema("/config/avro/clickhouse");
        System.out.println("clickhouse schema dynamic 检测完成");
    }


    @Test
    public void testDruidSchema(){
        checkDruidSchema("/config/avro/druid");
        System.out.println("druid schema dynamic 检测完成");
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
        ResponseEntity<List> result = restTemplate.postForEntity("http://" + druidIoProperties.getUrl(), requestEntity, List.class);
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
                    try {
                        Schema ckSchema = new Schema.Parser().parse(new File(f.getPath()));
                        cKSchema.put(ckSchema.getName(), ckSchema);
                    }catch (Exception ex){
                        log.error("遍历" + path + "路径下文件失败: " + f.getName(), ex);
                    }
                }
            }
        } catch (Exception e) {
            log.error("获取 项目路径/{}{}失败", path);
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
