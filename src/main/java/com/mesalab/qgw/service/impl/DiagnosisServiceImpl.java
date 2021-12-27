package com.mesalab.qgw.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileReader;
import cn.hutool.core.io.file.FileWriter;
import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
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
import com.mesalab.common.enums.*;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.sqlparser.TableColumnVisitor;
import com.mesalab.knowledge.common.utils.HttpConfig;
import com.mesalab.qgw.benchmark.clickhouse.ClickhouseWriter;
import com.mesalab.qgw.benchmark.druid.DruidWriter;
import com.mesalab.qgw.benchmark.engine.EngineWriter;
import com.mesalab.qgw.benchmark.hbase.HbaseWriter;
import com.mesalab.qgw.dialect.HbaseDialect;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.ClickHouseHttpQuery;
import com.mesalab.qgw.model.api.ClickHouseHttpSource;
import com.mesalab.qgw.model.api.DruidIoHttpSource;
import com.mesalab.qgw.service.DiagnosisService;
import com.mesalab.qgw.service.MetadataService;
import com.zdjizhi.utils.Encodes;
import com.zdjizhi.utils.JsonMapper;
import com.zdjizhi.utils.StringUtil;
import lombok.Data;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import org.apache.avro.AvroRuntimeException;
import org.apache.avro.Schema;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service("diagnosisService")
public class DiagnosisServiceImpl implements DiagnosisService {
    private static final Log log = LogFactory.get();
    private static final String AVRO_SUFFIX = ".avsc";
    private static final String METADATA_PREFIX = "Metadata/";
    private static final String SCHEMA_PREFIX = "Schema/";
    private static final int DEFAULT_SERVER_PORT = 9999;
    private static final String DRUID_PREFIX = "druid_";
    private static final String CK_PREFIX = "ck_";
    private static final String ENGINE_PREFIX = "engine_";
    private static final String HBASE_PREFIX = "hbase_";

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
    @Autowired
    private HbaseWriter hbaseWriter;
    @Autowired
    private ClickHouseHttpSource clickHouseHttpSource;
    @Autowired
    private DruidIoHttpSource druidIoHttpSource;
    @Autowired
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
            Map<String, Map<String, String>> clickhouseResult = parseSchemaByDatasource(clickHouseHttpSource.getDbName(), metadataService.getAllTable());
            Map<String, Map<String, String>> druidResult = parseSchemaByDatasource(druidIoHttpSource.getDbname(), metadataService.getAllTable());
            Map<String, Map<String, String>> mergeResult = Stream
                    .concat(clickhouseResult.entrySet().stream(), druidResult.entrySet().stream())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            long executeTime = watch.elapsed(TimeUnit.MILLISECONDS);
            statistics.put("elapsed", executeTime);
            result = BaseResultGenerator.success("Avro Schema Validation Success.", mergeResult, statistics);
            result.setFormatType(QueryFormatEnum.JSON.getValue());
        } catch (RuntimeException e) {
            throw new BusinessException(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    ResultCodeEnum.ENGINE_PARSE_ERROR.getCode(), "Avro Schema Validation Fail:", e);

        }
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
            Map<String, Map<String, String>> clickhouseResult = checkMetadataByDatasource(clickHouseHttpSource.getDbName(), metadataService.getAllTable());
            Map<String, Map<String, String>> druidResult = checkMetadataByDatasource(druidIoHttpSource.getDbname(), metadataService.getAllTable());
            Map<String, Map<String, String>> mergeResult = Stream
                    .concat(clickhouseResult.entrySet().stream(), druidResult.entrySet().stream())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            statistics.put("elapsed", watch.elapsed(TimeUnit.MILLISECONDS));
            result = BaseResultGenerator.success("Metadata Validation Success, Only Table Exist Fields detail.", mergeResult, statistics);
            result.setFormatType(QueryFormatEnum.JSON.getValue());
        } catch (RuntimeException e) {
            throw new BusinessException(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(), "Metadata Validation Fail:", e);
        }
        return result;
    }


    @Override
    public BaseResult runPocSQL(boolean isSaved, String option) {
        BaseResult result = null;
        Map<String, Object> statistics = Maps.newLinkedHashMap();
        Map<String, Map<String, String>> mergeResult;
        Map<String, Map<String, String>> engineResult;
        Map<String, Map<String, String>> clickhouseResult;
        Map<String, Map<String, String>> druidResult;
        Map<String, Map<String, String>> hbaseResult;
        try {
            Stopwatch watch = Stopwatch.createStarted();

            Optional<File> druidTestFile = Optional.of(new File(druidWriter.buildPocSQL()));
            druidResult = executePocSqlByDatasource(druidTestFile, isSaved, option);

            Optional<File> ckTestFile = Optional.of(new File(clickhouseWriter.buildPocSQL()));
            clickhouseResult = executePocSqlByDatasource(ckTestFile, isSaved, option);

            Optional<File> hbaseTestFile = Optional.of(new File(hbaseWriter.buildPocSQL()));
            hbaseResult = executePocSqlByDatasource(hbaseTestFile, isSaved, option);

            Optional<File> engineTestFile = Optional.of(new File(engineWriter.buildPocSQL()));
            engineResult = executePocSqlByDatasource(engineTestFile, isSaved, option);

            mergeResult = Stream
                    .concat(clickhouseResult.entrySet().stream(), druidResult.entrySet().stream())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            mergeResult = Stream.concat(mergeResult.entrySet().stream(), engineResult.entrySet().stream())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            mergeResult = Stream.concat(mergeResult.entrySet().stream(), hbaseResult.entrySet().stream())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            statistics.put("elapsed", watch.elapsed(TimeUnit.MILLISECONDS));
            result = BaseResultGenerator.success("SQL Queries Success.", mergeResult, statistics);
            result.setFormatType(QueryFormatEnum.JSON.getValue());
        } catch (RuntimeException e) {
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
        } catch (RuntimeException e) {
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
     * @param option  SQL诊断方式
     * @return
     */
    private Map<String, Map<String, String>> executePocSqlByDatasource(Optional<File> file, boolean isSaved, String option) {
        Map<String, Map<String, String>> resultMap = Maps.newLinkedHashMap();
        Map<String, String> infoMap = Maps.newLinkedHashMap();
        Map<String, Object> paramMap = Maps.newHashMap();
        if (DiagnosisOptionEnum.VALIDATION.getValue().equalsIgnoreCase(option)) {
            paramMap.put("option", QueryOptionEnum.SYNTAX_VALIDATION.getValue());
        } else if (DiagnosisOptionEnum.EXECUTION.getValue().equalsIgnoreCase(option)) {
            paramMap.put("option", QueryOptionEnum.REAL_TIME.getValue());
        }
        paramMap.put("format", QueryFormatEnum.CSV.getValue());
        FileWriter writer = null;
        int index = 0;
        if (file.isPresent() && file.get().isFile()) {
            String benchFileName = file.get().getName();
            List<String> rawLines = new FileReader(file.get()).readLines();
            if (isSaved) {
                writer = new FileWriter(file.get().getAbsolutePath() + "." + IdUtil.simpleUUID() + ".dat");
            }
            if (StringUtil.lowerCase(benchFileName).startsWith(DRUID_PREFIX)) {
                for (String line : rawLines) {
                    index++;
                    if (Strings.isNullOrEmpty(line)) {
                        continue;
                    }
                    BaseResult queryResult = generateDruidBaseResult(line);
                    resultProcess(file, infoMap, writer, index, queryResult);
                }
            } else if (StringUtil.lowerCase(benchFileName).startsWith(CK_PREFIX)) {
                for (String line : rawLines) {
                    index++;
                    if (Strings.isNullOrEmpty(line)) {
                        continue;
                    }
                    BaseResult queryResult = generateClickHouseBaseResult(line);
                    resultProcess(file, infoMap, writer, index, queryResult);
                }
            } else if (StringUtil.lowerCase(benchFileName).startsWith(HBASE_PREFIX)) {
                for (String line : rawLines) {
                    index++;
                    if (Strings.isNullOrEmpty(line)) {
                        continue;
                    }
                    BaseResult queryResult = generateHbaseBaseResult(line);
                    resultProcess(file, infoMap, writer, index, queryResult);
                }
            } else if (StringUtil.lowerCase(benchFileName).startsWith(ENGINE_PREFIX)) {
                for (String line : rawLines) {
                    index++;
                    if (Strings.isNullOrEmpty(line)) {
                        continue;
                    }
                    Map<String, String> queryResult = executeQuery(line, paramMap);
                    int statusCode = Integer.parseInt(queryResult.get("status").trim());
                    if (statusCode == HttpStatus.SC_OK) {
                        if (writer != null) {
                            writer.append("--Query" + index + "\t" + "Elapsed(ms):" + JsonPath.read(queryResult.get("result"), "$.statistics.elapsed") + "\n");
                            writer.append(JsonPath.read(queryResult.get("result"), "$.meta") + "\n");
                            writer.append(JsonPath.read(queryResult.get("result"), "$.data") + "\n");
                        }
                        infoMap.put("Query" + index, JsonPath.read(queryResult.get("result"), "$.statistics.elapsed") + "");
                    } else if (statusCode == HttpStatus.SC_GATEWAY_TIMEOUT || statusCode == HttpStatus.SC_BAD_GATEWAY) {
                        infoMap.put("Query" + index, "status:" + queryResult.get("status") + ",message:" + queryResult.get("message"));

                    } else {
                        throw new BusinessException(statusCode, ResultCodeEnum.SQL_EXECUTION_ERROR.getCode(), file.get().getName()
                                + ",Query" + index + "," + queryResult.get("status") + "," + queryResult.get("message"), null);
                    }
                }
            }
            resultMap.put(file.get().getName(), infoMap);
        } else {
            log.error("File is not exist or is directory.");
        }
        return resultMap;
    }

    private void resultProcess(Optional<File> file, Map<String, String> infoMap, FileWriter writer, int index, BaseResult queryResult) {
        int statusCode = Integer.parseInt(queryResult.getStatus().toString());
        if (statusCode == HttpStatus.SC_OK) {
            if (writer != null) {
                writer.append("--Query" + index + "\t" + "Elapsed(ms):" + queryResult.getStatistics().get("elapsed") + "\n");
                writer.append(queryResult.getMeta() + "\n");
                writer.append(queryResult.getData() + "\n");
            }
            infoMap.put("Query" + index, queryResult.getStatistics().get("elapsed") + "");
        } else if (statusCode == HttpStatus.SC_GATEWAY_TIMEOUT || statusCode == HttpStatus.SC_BAD_GATEWAY) {
            infoMap.put("Query" + index, "status:" + queryResult.getStatus() + ",message:" + queryResult.getMessage());

        } else {
            throw new BusinessException(statusCode, ResultCodeEnum.SQL_EXECUTION_ERROR.getCode(), file.get().getName()
                    + ",Query" + index + "," + queryResult.getStatus() + "," + queryResult.getMessage(), null);
        }
    }

    /**
     * @param sql
     * @return com.mesalab.common.base.BaseResult
     * @Description druid SQL测试集在Druid数据源查询并获取查询结果
     * @author wanghao
     * @date 2021/9/9 17:42
     */
    private BaseResult generateDruidBaseResult(String sql) {
        BaseResult baseResult;
        Object dataObject;
        Stopwatch watch = Stopwatch.createUnstarted();
        watch.start();
        Map<String, String> results = druidExecuteHttpPost(sql);
        if (Integer.parseInt(results.get("status")) == HttpStatus.SC_OK) {
            baseResult = BaseResultGenerator.generate(Integer.valueOf(results.get("status")), ResultCodeEnum.EXECUTE_SUCCESS.getCode(), "ok",
                    (List<Object>) com.mesalab.common.utils.JsonMapper.fromJsonString(results.get("result"), Object.class), null, null, QueryFormatEnum.JSON.getValue());
            dataObject = baseResult.getData();
            List<Object> allResults = (List<Object>) dataObject;
            Map<String, Object> statistics = Maps.newLinkedHashMap();
            statistics.put("elapsed", watch.elapsed(TimeUnit.MILLISECONDS));
            statistics.put("rows_read", allResults.size());
            baseResult.setData(allResults);
            baseResult.setStatistics(statistics);
        } else {
            baseResult = BaseResultGenerator.generate(Integer.parseInt(results.get("status")), ResultCodeEnum.SQL_EXECUTION_ERROR.getCode(), results.get("message"),
                    results.get("result"), null, null, QueryFormatEnum.JSON.getValue());
            dataObject = baseResult.getData();
            baseResult.setMessage("SQL Execution Error: ".concat(JsonPath.read(dataObject.toString(), "$.errorMessage")));
        }
        return baseResult;
    }


    private Map<String, String> druidExecuteHttpPost(String sql) {
        String queryURL = URLUtil.normalize(druidIoHttpSource.getUrl());
        DruidQueryParam druidQueryParam = new DruidQueryParam();
        druidQueryParam.setQuery(sql);
        druidQueryParam.getContext().put("skipEmptyBuckets", druidIoHttpSource.getSkipEmptyBuckets());
        druidQueryParam.setResultFormat("object");
        log.info("DB Engine is :{}, Execute Query is: {}", DBTypeEnum.DRUID.getValue(), com.mesalab.common.utils.JsonMapper.toJsonString(druidQueryParam));
        int socketTimeOut = httpConfig.getDruidSocketTimeOut();
        return httpClientService.httpPost(queryURL, com.mesalab.common.utils.JsonMapper.toJsonString(druidQueryParam), socketTimeOut);
    }


    /**
     * @param sql
     * @return com.mesalab.common.base.BaseResult
     * @Description ck SQL测试集在ClickHouse数据源查询并获取查询结果
     * @author wanghao
     * @date 2021/9/9 17:39
     */
    private BaseResult generateClickHouseBaseResult(String sql) {
        BaseResult baseResult;
        Object dataObject;
        Map<String, String> results = clickHouseExecuteHttpGet(sql);
        if (Integer.parseInt(results.get("status")) == HttpStatus.SC_OK) {
            baseResult = BaseResultGenerator.generate(Integer.parseInt(results.get("status")), ResultCodeEnum.EXECUTE_SUCCESS.getCode(), "ok",
                    com.mesalab.common.utils.JsonMapper.fromJsonString(results.get("result"), Map.class), null, null, QueryFormatEnum.JSON.getValue());
            dataObject = baseResult.getData();
            Map<String, Object> resultMap = (Map<String, Object>) dataObject;
            Map<String, Object> statisticMap = (Map<String, Object>) resultMap.get("statistics");
            Map<String, Object> statistics = Maps.newLinkedHashMap();
            statistics.put("elapsed", Math.round(Double.parseDouble(String.valueOf(statisticMap.get("elapsed"))) * 1000));
            statistics.put("rows_read", statisticMap.get("rows_read"));
            baseResult.setData(resultMap.get("data"));
            baseResult.setStatistics(statistics);
        } else {
            baseResult = BaseResultGenerator.generate(Integer.parseInt(results.get("status")), ResultCodeEnum.SQL_EXECUTION_ERROR.getCode(), null,
                    results.get("result"), null, null, QueryFormatEnum.JSON.getValue());
            dataObject = baseResult.getData();
            baseResult.setStatus((baseResult.getStatus() < 500) || (baseResult.getStatus() > 400) ? HttpStatus.SC_INTERNAL_SERVER_ERROR : baseResult.getStatus());
            baseResult.setMessage("SQL Execution Error: ".concat(JsonPath.read(dataObject.toString(), "$.errorMessage")));
        }
        return baseResult;
    }

    private Map<String, String> clickHouseExecuteHttpGet(String sql) {
        String ckQuery = Encodes.urlEncode(sql);
        String queryURL = URLUtil.normalize(clickHouseHttpSource.getUrl() + "/?");
        StringBuilder queryParamBuilder = new StringBuilder("user=")
                .append(clickHouseHttpSource.getRealTimeAccountUserName()).append("&")
                .append("password=").append(clickHouseHttpSource.getRealTimeAccountPin()).append("&")
                .append("database=").append(clickHouseHttpSource.getDbName()).append("&")
                .append("query=").append(ckQuery)
                .append(" FORMAT JSON;");
        ClickHouseHttpQuery clickHouseHttpQuery = new ClickHouseHttpQuery();
        clickHouseHttpQuery.setQueryParameter(queryParamBuilder.toString());
        log.info("DB Engine is :{}, Execute Http Query is: {}", DBTypeEnum.CLICKHOUSE.getValue(), com.mesalab.common.utils.JsonMapper.toJsonString(clickHouseHttpQuery));
        List<NameValuePair> values = URLEncodedUtils.parse(clickHouseHttpQuery.getQueryParameter(), Charset.forName("UTF-8"));
        return httpClientService.httpGet(queryURL + URLEncodedUtils.format(values, "utf-8"), clickHouseHttpQuery.getSocketTimeOut());
    }


    /**
     * @param sql
     * @return com.mesalab.common.base.BaseResult
     * @Description hbase SQL测试集在hbase数据源查询并获取查询结果
     * @author wanghao
     * @date 2021/11/11 10:01
     */
    private BaseResult generateHbaseBaseResult(String sql) {
        BaseResult baseResult = null;
        HbaseDialect hbaseDialect = new HbaseDialect(queryBuild(sql));
        baseResult = hbaseDialect.generateBaseResult(converter(sql), Optional.of("OK"));
        return baseResult;
    }

    private ApiParam queryBuild(String sql) {
        return ApiParam.builder().format(QueryFormatEnum.JSON.getValue()).query(sql).build();
    }

    private String converter(String sql) {
        Statement parse;
        try {
            parse = CCJSqlParserUtil.parse(sql);
        } catch (JSQLParserException e) {
            log.error("SQL Syntax Error: Converter SQL Syntax Error, SQL is:{}, Error is:{}", sql, e);
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.SQL_SYNTAX_ERROR.getCode(), "Converter SQL Syntax Error.", e);
        }
        TableColumnVisitor visitor = new TableColumnVisitor();
        visitor.surroundWithDoubleQuote(parse);
        return parse.toString();
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
     * 对指定数据源dbName下的schema 与数据库的字段进行对比检查
     *
     * @param dbName
     * @param tables
     * @return
     */
    private Map<String, Map<String, String>> checkMetadataByDatasource(String dbName, List<String> tables) {

        Map<String, Map<String, String>> resultMap = Maps.newLinkedHashMap();
        Map<String, String> infoMap = Maps.newHashMap();
        for (String tableName : tables) {
            Schema schema = metadataService.getSchemaByName(tableName);
            if (!dbName.equals(schema.getNamespace())) continue;
            Map<String, String> result = executeQuery("describe " + tableName, null);
            if (HttpStatus.SC_OK != Integer.parseInt(result.get("status"))
                    || Integer.parseInt(JsonPath.read(result.get("result"), "$.status").toString()) != HttpStatus.SC_OK) {
                log.error("查询表结构失败：" + result.get("result"));
                throw new BusinessException(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        ResultCodeEnum.SQL_EXECUTION_ERROR.getCode(),
                        " The Table[" + tableName + "] Structure query failed", null);
            }
            List<Map<String, Object>> dbFields = JsonPath.read(result.get("result"), "$.data.fields.*");

            Map<String, Object> dbFieldMap = Maps.newHashMap();
            dbFields.stream().forEach(objectMap -> {
                dbFieldMap.put(objectMap.get("name").toString(), objectMap.get("type"));
            });
            List<Map<String, Object>> schemaFields = JsonPath.read(schema.toString(), "$.fields.*");
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


        resultMap.put(METADATA_PREFIX + dbName, infoMap);

        return resultMap;
    }


    private Map<String, String> executeQuery(String sql, Map<String, Object> urlParam) {
        Map<String, String> resultMap = Maps.newHashMap();
        try {
            if (serverPort == 0) {
                serverPort = DEFAULT_SERVER_PORT;
            }
            String param = URLUtil.buildQuery(urlParam, CharsetUtil.CHARSET_UTF_8);
            sql = URLEncoder.encode(sql, "utf-8").replaceAll("\\+", "%20");
            String queryURL = URLUtil.normalize(localHostAddress + ":" + serverPort + "/?query=");
            int socketTimeOut = httpConfig.getServerResponseTimeOut();
            resultMap = httpClientService.httpGet(queryURL + sql + (StrUtil.isBlank(param) ? "" : "&" + param), socketTimeOut);
        } catch (UnsupportedEncodingException e) {
            log.error("Unsupported Encoding error: ", e);
        }

        return resultMap;
    }


    /**
     * 对指定的数据源dbName下的schema进行静态检查
     *
     * @param dbName
     * @param tables
     * @return
     */
    private Map<String, Map<String, String>> parseSchemaByDatasource(String dbName, List<String> tables) {
        Map<String, Map<String, String>> resultMap = Maps.newLinkedHashMap();
        Map<String, String> infoMap = Maps.newHashMap();
        for (String tableName : tables) {
            Schema schema = metadataService.getSchemaByName(tableName);
            Map schemaMap = (Map) JsonMapper.fromJsonString(schema.toString(), Map.class);
            if (!dbName.equals(schema.getNamespace())) continue;
            List<Map<String, Object>> fields = (List<Map<String, Object>>) schemaMap.get("fields");
            infoMap.put(tableName, fields.size() + " Fields");
        }
        resultMap.put(SCHEMA_PREFIX + dbName, infoMap);

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
                for (File file : FileUtil.loopFiles(schemaDir)) {
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

    @Data
    class DruidQueryParam {
        private String query;
        private Map<String, String> context = Maps.newHashMap();
        private String resultFormat;
    }


}
