package com.mesalab.qgw.service.impl;

import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.base.Stopwatch;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.jayway.jsonpath.JsonPath;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.QueryFormatEnum;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.knowledge.common.utils.HttpConfig;
import com.mesalab.qgw.service.DiagnosisService;
import com.zdjizhi.utils.JsonMapper;
import com.zdjizhi.utils.StringUtil;
import org.apache.avro.Schema;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service("diagnosisService")
public class DiagnosisServiceImpl implements DiagnosisService {
    private static final Log log = LogFactory.get();
    private Stopwatch watch = Stopwatch.createUnstarted();
    private static final String CK_SCHEMA_DIR = File.separator + "config"
            + File.separator + "avro" +  File.separator + "clickhouse";
    private static final String DRUID_SCHEMA_DIR = File.separator + "config"
            + File.separator + "avro" +  File.separator + "druid";
    private static final String AVRO_SUFFIX = ".avsc";
    private static final String METADATA_PREFIX = "Metadata";
    private static final String SCHEMA_PREFIX = "Schema";

    @Autowired
    HttpClientService httpClientService;
    @Autowired
    HttpConfig httpConfig;
    @Value("${server.port}")
    private int serverPort;
    private static String localHostAddress;
    static {
        localHostAddress = NetUtil.getLocalhostStr();
    }

    /**
     * 验证Schema格式是否正确
     * @return
     */
    @Override
    public BaseResult validateSchema() {
        BaseResult result = null;
        try {
            watch.start();
            Map<String, Object> statistics = Maps.newLinkedHashMap();
            Map<String, Map<String, String>> clickhouseResult = parseSchemaByDatasource(CK_SCHEMA_DIR);
            Map<String, Map<String, String>> druidResult = parseSchemaByDatasource(DRUID_SCHEMA_DIR);
            Map<String, Map<String, String>> mergeResult = Stream
                    .concat(clickhouseResult.entrySet().stream(), druidResult.entrySet().stream())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            long executeTime = watch.elapsed(TimeUnit.MILLISECONDS);
            watch.reset();
            statistics.put("elapsed", executeTime);
            result = BaseResultGenerator.success("Avro Schema Validation Success.", mergeResult, statistics);
            result.setFormatType(QueryFormatEnum.JSON.getValue());
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    ResultCodeEnum.ENGINE_PARSE_ERROR.getCode(), "Avro Schema Validation Fail:" , e);

        }

        return result;
    }

    /**
     * 验证schema与数据库表结构是否一致
     * @return
     */
    @Override
    public BaseResult validateMetadata() {
        BaseResult result = null;
        try {
            watch.start();
            Map<String, Object> statistics = Maps.newLinkedHashMap();
            Map<String, Map<String, String>> clickhouseResult = checkMetadataByDatasource(CK_SCHEMA_DIR);
            Map<String, Map<String, String>> druidResult = checkMetadataByDatasource(DRUID_SCHEMA_DIR);
            Map<String, Map<String, String>> mergeResult = Stream
                    .concat(clickhouseResult.entrySet().stream(), druidResult.entrySet().stream())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            statistics.put("elapsed", watch.elapsed(TimeUnit.MILLISECONDS));
            result = BaseResultGenerator.success("Metadata Validation Success, Only Table Exist Fields detail.", mergeResult, statistics);
            result.setFormatType(QueryFormatEnum.JSON.getValue());
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(), "Metadata Validation Fail:" , e);
        } finally {
            watch.reset();
        }
        return result;
    }



    private Map<String, Map<String, String>> checkMetadataByDatasource(String path) {
        Map<String, Schema>  schemas = getSchemasByDatasource(path);
        Map<String, Map<String, String>> resultMap = Maps.newLinkedHashMap();
        Map<String, String> infoMap = Maps.newHashMap();
        for (String tableName : schemas.keySet()) {

            Map<String, String> result = executeQuery("describe " + tableName);
            List<Map<String,Object>> dbFields = JsonPath.read(result.get("result"),"$.data.fields.*");

            Map<String, Object> dbFieldMap = Maps.newHashMap();
            dbFields.stream().forEach( objectMap -> {
                dbFieldMap.put(objectMap.get("name").toString(), objectMap.get("type"));
            });
            List<Map<String,Object>> schemaFields = JsonPath.read(schemas.get(tableName).toString(), "$.fields.*");
            Map<String, Object> schemaFieldMap = Maps.newHashMap();
            schemaFields.stream().forEach( objectMap -> {
                schemaFieldMap.put(objectMap.get("name").toString(), objectMap.get("type"));
            });

            MapDifference<String, Object> difference =  Maps.difference(schemaFieldMap, dbFieldMap);

            Map<String, Object> entriesOnlyOnLeft = difference.entriesOnlyOnLeft();

            if (entriesOnlyOnLeft.size() > 0) {
                log.error("键只存在于左边Map的映射项:" + entriesOnlyOnLeft);
                throw new BusinessException(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        ResultCodeEnum.ENGINE_PARSE_ERROR.getCode(),
                        " Schema Fields greater than Table Fields,Schema " + tableName + " exist fields " + entriesOnlyOnLeft , null);

            }

            Map<String, Object> entriesOnlyOnRight = difference.entriesOnlyOnRight();
            log.warn("键只存在于右边Map的映射项:" + entriesOnlyOnRight);
            infoMap.put(tableName, entriesOnlyOnRight.toString());
        }


        resultMap.put(METADATA_PREFIX + path, infoMap);

        return  resultMap;
    }


    private Map<String, String> executeQuery(String sql) {
        Map<String, String> resultMap = Maps.newHashMap();
        try{
            sql = URLEncoder.encode(sql, "utf-8").replaceAll("\\+", "%20");
            String queryURL = URLUtil.normalize(localHostAddress + ":" + serverPort + "/?query=");
            int socketTimeOut = httpConfig.getServerResponseTimeOut();
            resultMap = httpClientService.httpGet(queryURL + sql, socketTimeOut);
        } catch (UnsupportedEncodingException e) {
            log.error("sql Encode error: ", e);
        }

        return resultMap;
    }



    /**
     * 对指定的数据源下的schema进行静态检查
     * @param path
     * @return
     */
    private Map<String, Map<String, String>> parseSchemaByDatasource(String path) {
        Map<String, Schema> schemas = getSchemasByDatasource(path);
        Map<String, Map<String, String>> resultMap = Maps.newLinkedHashMap();
        Map<String, String> infoMap = Maps.newHashMap();
        for (String tableName : schemas.keySet()) {
            Map schemaMap = (Map) JsonMapper.fromJsonString(schemas.get(tableName).toString(), Map.class);
            List<Map<String,Object>> fields = (List<Map<String,Object>>) schemaMap.get("fields");
            infoMap.put(tableName, fields.size() + " Fields");
        }
        resultMap.put(SCHEMA_PREFIX + path, infoMap);

        return resultMap;
    }


    /**
     * 获取当前路径下的schema对象列表
     * @param path avro 路径
     * @return
     */
    private Map<String, Schema> getSchemasByDatasource(String path) {
        Map<String, Schema> schemas = Maps.newLinkedHashMap();
        try {
            File schemaDir = new File(new File("").getCanonicalPath() + path);
            if (schemaDir.exists() && schemaDir.isDirectory()) {
                for (File file : schemaDir.listFiles()) {
                   try {
                       if (StringUtil.lowerCase(file.getName()).endsWith(AVRO_SUFFIX)) {
                           Schema schema = new Schema.Parser().parse(file);
                           schemas.put(schema.getName(), schema);
                       } else {
                           log.warn("UnKnow format file {} ", file.getName());
                       }
                   } catch (Exception e) {
                       log.error("Schema Parsing Error {}, Message is {}", file.getName(), e);
                       throw new BusinessException(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                               ResultCodeEnum.ENGINE_PARSE_ERROR.getCode(), file.getName() + " Parse Error: " + e.getMessage() , e);
                   }
                }
            }
        } catch (IOException e) {
            log.error("The current directory {} does not exist.", path);
        }
        return schemas;
    }





}
