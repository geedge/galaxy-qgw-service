package com.mesalab.qgw.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileReader;
import cn.hutool.core.io.file.FileWriter;
import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.jayway.jsonpath.JsonPath;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.DBTypeEnum;
import com.mesalab.common.enums.QueryFormatEnum;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.SchemaCacheUtils;
import com.mesalab.knowledge.common.utils.HttpConfig;
import com.mesalab.qgw.benchmark.clickhouse.ClickhouseWriter;
import com.mesalab.qgw.benchmark.druid.DruidWriter;
import com.mesalab.qgw.benchmark.engine.EngineWriter;
import com.mesalab.qgw.service.DiagnosisService;
import com.mesalab.qgw.service.MetadataService;
import com.zdjizhi.utils.JsonMapper;
import com.zdjizhi.utils.StringUtil;
import org.apache.avro.AvroRuntimeException;
import org.apache.avro.Schema;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service("diagnosisService")
public class DiagnosisServiceImpl implements DiagnosisService {
    private static final Log log = LogFactory.get();
    private static final String CK_SCHEMA_DIR = File.separator + "config"
            + File.separator + "avro" + File.separator + "clickhouse";
    private static final String DRUID_SCHEMA_DIR = File.separator + "config"
            + File.separator + "avro" + File.separator + "druid";
    private static final String AVRO_SUFFIX = ".avsc";
    private static final String METADATA_PREFIX = "Metadata";
    private static final String SCHEMA_PREFIX = "Schema";
    private static final int DEFAULT_SERVER_PORT = 9999;

    @Autowired
    private HttpClientService httpClientService;
    @Autowired
    private HttpConfig httpConfig;
    @Value("${server.port}")
    private int serverPort;
    @Autowired
    private ClickhouseWriter clickhouseWriter;
    @Autowired
    private EngineWriter engineWriter;
    @Autowired
    private DruidWriter druidWriter;

    @Resource(name = "nacosMetadataService")
    private MetadataService metadataService;

    private static final String localHostAddress = NetUtil.getLocalhostStr();

    /**
     * 验证Schema格式是否正确
     *
     * @return
     */
    @Override
    public BaseResult validateSchema() {
        BaseResult result = null;
        try {
            Stopwatch watch = Stopwatch.createStarted();
            Map<String, Object> statistics = Maps.newLinkedHashMap();
            Map<String, Map<String, String>> clickhouseResult = parseSchemaByDatasource(CK_SCHEMA_DIR);
            Map<String, Map<String, String>> druidResult = parseSchemaByDatasource(DRUID_SCHEMA_DIR);
            Map<String, Map<String, String>> mergeResult = Stream
                    .concat(clickhouseResult.entrySet().stream(), druidResult.entrySet().stream())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            long executeTime = watch.elapsed(TimeUnit.MILLISECONDS);
            statistics.put("elapsed", executeTime);
            result = BaseResultGenerator.success("Avro Schema Validation Success.", mergeResult, statistics);
            result.setFormatType(QueryFormatEnum.JSON.getValue());
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    ResultCodeEnum.ENGINE_PARSE_ERROR.getCode(), "Avro Schema Validation Fail:", e);

        }
        SchemaCacheUtils.removeAll();
        return result;
    }

    /**
     * 验证schema与数据库表结构是否一致
     *
     * @return
     */
    @Override
    public BaseResult validateMetadata() {
        BaseResult result = null;
        try {
            Stopwatch watch = Stopwatch.createStarted();
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
                    ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(), "Metadata Validation Fail:", e);
        }
        return result;
    }


    @Override
    public BaseResult runPocSQL(boolean isSaved) {
        BaseResult result = null;
        Map<String, Object> statistics = Maps.newLinkedHashMap();
        Map<String, Map<String, String>> mergeResult;
        Map<String, Map<String, String>> engineResult;
        Map<String, Map<String, String>> clickhouseResult;
        Map<String, Map<String, String>> druidResult;
        try {
            Stopwatch watch = Stopwatch.createStarted();

            Optional<File> druidTestFile = Optional.of(new File(druidWriter.buildPocSQL()));
            druidResult = executePocSqlByDatasource(druidTestFile, isSaved);

            Optional<File> ckTestFile = Optional.of(new File(clickhouseWriter.buildPocSQL()));
            clickhouseResult = executePocSqlByDatasource(ckTestFile, isSaved);

            Optional<File> engineTestFile = Optional.of(new File(engineWriter.buildPocSQL()));
            engineResult = executePocSqlByDatasource(engineTestFile, isSaved);


            mergeResult = Stream
                    .concat(clickhouseResult.entrySet().stream(), druidResult.entrySet().stream())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            mergeResult = Stream.concat(mergeResult.entrySet().stream(), engineResult.entrySet().stream())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            statistics.put("elapsed", watch.elapsed(TimeUnit.MILLISECONDS));
            result = BaseResultGenerator.success("SQL Queries Success.", mergeResult, statistics);
            result.setFormatType(QueryFormatEnum.JSON.getValue());
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.SC_BAD_REQUEST,
                    ResultCodeEnum.SQL_EXECUTION_ERROR.getCode(), "Execute Poc SQL Fail:", e);
        }
        return result;
    }

    @Override
    public BaseResult getPocSQL(String dialect, Integer queryNo) {
        BaseResult result;
        Optional<File> testFile;
        Map<String, Object> statistics = Maps.newLinkedHashMap();
        Map<String, String> dialectResult = Maps.newHashMap();

        if (dialect.equalsIgnoreCase(DBTypeEnum.CLICKHOUSE.getValue())) {
            testFile = Optional.of(new File(clickhouseWriter.buildPocSQL()));
        } else if (dialect.equalsIgnoreCase(DBTypeEnum.ENGINE.getValue())) {
            testFile = Optional.of(new File(engineWriter.buildPocSQL()));
        } else if (dialect.equalsIgnoreCase(DBTypeEnum.DRUID.getValue())) {
            testFile = Optional.of(new File(druidWriter.buildPocSQL()));
        } else {
            throw new BusinessException(HttpStatus.SC_BAD_REQUEST,
                    ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(), "Illegal value about dialect.");
        }


        try {
            Stopwatch watch = Stopwatch.createStarted();


            if (testFile.isPresent()) {
                if (!testFile.get().isFile()) {
                    log.warn("UnKnow File " + testFile.get());
                    return null;
                }
                dialectResult = getPocSqlByDatasource(testFile, queryNo);
            }

            statistics.put("elapsed", watch.elapsed(TimeUnit.MILLISECONDS));
            result = BaseResultGenerator.success("Get SQL Success.", dialectResult, statistics);
            result.setFormatType(QueryFormatEnum.JSON.getValue());
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.SC_BAD_REQUEST,
                    ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(), "Get Poc SQL Fail:", e);
        }
        return result;
    }


    /**
     * 执行POC SQL测试集，并返回执行结果
     *
     * @param file    sql 查询语句列表
     * @param isSaved 是否保存查询结果
     * @return
     */
    private Map<String, Map<String, String>> executePocSqlByDatasource(Optional<File> file, boolean isSaved) {
        Map<String, Map<String, String>> resultMap = Maps.newLinkedHashMap();
        Map<String, String> infoMap = Maps.newLinkedHashMap();
        List<String> rawLines = new FileReader(file.get()).readLines();
        FileWriter writer = null;
        int index = 0;

        if (file.isPresent() && file.get().isFile()) {
            if (isSaved) {
                writer = new FileWriter(file.get().getAbsolutePath() + "." + IdUtil.simpleUUID() + ".dat");
            }
            for (String line : rawLines) {
                index++;
                if (Strings.isNullOrEmpty(line)) {
                    continue;
                }
                Map<String, String> queryResult = executeQuery(line, Optional.of(QueryFormatEnum.CSV));
                int statusCode = Integer.valueOf(queryResult.get("status").trim());
                if (statusCode != HttpStatus.SC_OK) {
                    throw new BusinessException(statusCode, ResultCodeEnum.SQL_EXECUTION_ERROR.getCode(), "Query" + index + " : " + JsonPath.read(queryResult.get("result"), "$.message"), null);
                }
                if (writer != null) {
                    writer.append("--Query" + index + "\t" + "Elapsed(ms):" + JsonPath.read(queryResult.get("result"), "$.statistics.elapsed") + "\n");
                    writer.append(JsonPath.read(queryResult.get("result"), "$.meta") + "\n");
                    writer.append(JsonPath.read(queryResult.get("result"), "$.data") + "\n");
                }
                infoMap.put("Query" + index, JsonPath.read(queryResult.get("result"), "$.statistics.elapsed") + "");
            }

            resultMap.put(file.get().getName(), infoMap);

        } else {
            log.error("File is not exist or is directory.");
        }


        return resultMap;
    }


    private Map<String, String> getPocSqlByDatasource(Optional<File> file, Integer queryNo) {
        Map<String, String> infoMap = Maps.newLinkedHashMap();
        List<String> rawLines = new FileReader(file.get()).readLines();
        if (StringUtil.isEmpty(queryNo)) {
            int index = 0;
            for (String line : rawLines) {
                index++;
                infoMap.put("Query" + index, line);
            }
        } else {
            infoMap.put("Query" + queryNo, rawLines.get(queryNo - 1));
        }
        return infoMap;
    }


    /**
     * 对指定目录下的schema 与数据库的字段进行对比检查
     *
     * @param path schema 列表
     * @return
     */
    private Map<String, Map<String, String>> checkMetadataByDatasource(String path) {
        Map<String, Schema> schemas = new HashMap<>();
        if (path.equals(CK_SCHEMA_DIR)) {
            schemas = metadataService.getClickHouseSchema();
        }
        if (path.equals(DRUID_SCHEMA_DIR)) {
            schemas = metadataService.getDruidSchema();
        }

        Map<String, Map<String, String>> resultMap = Maps.newLinkedHashMap();
        Map<String, String> infoMap = Maps.newHashMap();
        for (String tableName : schemas.keySet()) {

            Map<String, String> result = executeQuery("describe " + tableName, Optional.empty());
            if (HttpStatus.SC_OK != Integer.parseInt(result.get("status"))) {
                log.error("查询表结构失败：" + result.get("result"));
                throw new BusinessException(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        ResultCodeEnum.SQL_EXECUTION_ERROR.getCode(),
                        " The Table Structure query failed", null);
            }
            List<Map<String, Object>> dbFields = JsonPath.read(result.get("result"), "$.data.fields.*");

            Map<String, Object> dbFieldMap = Maps.newHashMap();
            dbFields.stream().forEach(objectMap -> {
                dbFieldMap.put(objectMap.get("name").toString(), objectMap.get("type"));
            });
            List<Map<String, Object>> schemaFields = JsonPath.read(schemas.get(tableName).toString(), "$.fields.*");
            Map<String, Object> schemaFieldMap = Maps.newHashMap();
            schemaFields.stream().forEach(objectMap -> {
                schemaFieldMap.put(objectMap.get("name").toString(), objectMap.get("type"));
            });

            MapDifference<String, Object> difference = Maps.difference(schemaFieldMap, dbFieldMap);

            Map<String, Object> entriesOnlyOnLeft = difference.entriesOnlyOnLeft();

            if (entriesOnlyOnLeft.size() > 0) {
                log.error("键只存在于左边Map的映射项:" + entriesOnlyOnLeft);
                throw new BusinessException(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        ResultCodeEnum.ENGINE_PARSE_ERROR.getCode(),
                        " Schema Fields greater than Table Fields,Schema " + tableName + " exist fields " + entriesOnlyOnLeft, null);

            }
            Map<String, Object> entriesOnlyOnRight = difference.entriesOnlyOnRight();
            if (entriesOnlyOnRight.size() > 0) {
                log.warn("键只存在于右边Map的映射项:" + entriesOnlyOnRight);
                infoMap.put(tableName, entriesOnlyOnRight.toString());
            }

        }


        resultMap.put(METADATA_PREFIX + path, infoMap);

        return resultMap;
    }


    private Map<String, String> executeQuery(String sql, Optional<QueryFormatEnum> formatEnum) {
        Map<String, String> resultMap = Maps.newHashMap();
        try {

            if (serverPort == 0) {

                serverPort = DEFAULT_SERVER_PORT;
            }
            QueryFormatEnum format = formatEnum.orElse(QueryFormatEnum.JSON);
            sql = URLEncoder.encode(sql, "utf-8").replaceAll("\\+", "%20");
            String queryURL = URLUtil.normalize(localHostAddress + ":" + serverPort + "/?query=");
            int socketTimeOut = httpConfig.getServerResponseTimeOut();
            resultMap = httpClientService.httpGet(queryURL + sql + "&format=" + format, socketTimeOut);
        } catch (UnsupportedEncodingException e) {
            log.error("sql Encode error: ", e);
        }

        return resultMap;
    }


    /**
     * 对指定的数据源下的schema进行静态检查
     *
     * @param path
     * @return
     */
    private Map<String, Map<String, String>> parseSchemaByDatasource(String path) {
        Map<String, Schema> schemas = new HashMap<>();
        if (path.equals(CK_SCHEMA_DIR)) {
            schemas = metadataService.getClickHouseSchema();
        }
        if (path.equals(DRUID_SCHEMA_DIR)) {
            schemas = metadataService.getDruidSchema();
        }
        Map<String, Map<String, String>> resultMap = Maps.newLinkedHashMap();
        Map<String, String> infoMap = Maps.newHashMap();
        for (String tableName : schemas.keySet()) {
            Map schemaMap = (Map) JsonMapper.fromJsonString(schemas.get(tableName).toString(), Map.class);
            List<Map<String, Object>> fields = (List<Map<String, Object>>) schemaMap.get("fields");
            infoMap.put(tableName, fields.size() + " Fields");
        }
        resultMap.put(SCHEMA_PREFIX + path, infoMap);

        return resultMap;
    }


    /**
     * 获取当前路径下的schema对象列表
     *
     * @param path avro 路径
     * @return
     */
    private Map<String, Schema> getSchemasByDatasource(String path) {
        Map<String, Schema> schemas = Maps.newLinkedHashMap();
        try {
            File schemaDir = new File(new File("").getCanonicalPath() + path);
            if (FileUtil.isNotEmpty(schemaDir) && FileUtil.isDirectory(schemaDir)) {
                if (StringUtil.isEmpty(schemaDir.listFiles())) {
                    throw new BusinessException(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                            ResultCodeEnum.ENGINE_PARSE_ERROR.getCode(), "The Directory is null: " + path);
                }
                for (File file : Objects.requireNonNull(schemaDir.listFiles())) {
                    try {
                        if (StringUtil.lowerCase(file.getName()).endsWith(AVRO_SUFFIX)) {
                            Schema schema = new Schema.Parser().parse(file);
                            schemas.put(schema.getName(), schema);
                        } else {
                            log.warn("UnKnow format file {} ", file.getName());
                        }
                    } catch (AvroRuntimeException e) {
                        log.error("Schema Parsing Error {}, Message is {}", file.getName(), e);
                        throw new BusinessException(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                                ResultCodeEnum.ENGINE_PARSE_ERROR.getCode(), file.getName() + " Parse Error: " + e.getMessage(), e);
                    }
                }
            }
        } catch (IOException e) {
            log.error("The current directory {} does not exist.", path);
        }
        return schemas;
    }


}
