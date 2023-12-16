package com.mesalab.qgw.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.io.file.FileWriter;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson2.JSON;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.geedgenetworks.utils.CommonUtil;
import com.geedgenetworks.utils.DateUtils;
import com.geedgenetworks.utils.StringUtil;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.jayway.jsonpath.JsonPath;
import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.*;
import com.mesalab.common.nacos.NacosConfig;
import com.mesalab.qgw.benchmark.DialectWriter;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.qgw.model.basic.*;
import com.mesalab.qgw.service.DatabaseService;
import com.mesalab.qgw.service.QueryService;
import com.mesalab.qgw.service.TroubleshootingService;
import com.mesalab.qgw.service.DatasetService;
import org.apache.avro.Schema;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service("troubleshootingService")
public class TroubleshootingServiceImp implements TroubleshootingService, EnvironmentAware {

    private static final Log log = LogFactory.get();
    private static final String METADATA_PREFIX = "Metadata/";

    private static final String HBASE_DBNAME2 = "tsg_galaxy";
    private static final String BACKEND_ENGINE = "backend_engine";
    private static final String IDENTIFIER_NAME = "identifier_name";
    private static final String TEMPLATE = "template";

    private static final String DATASET_TYPE = "type";

    @Autowired
    private DatabaseService databaseService;
    @Autowired
    private ClickHouseHttpSource clickHouseHttpSource;
    @Autowired
    private DruidIoHttpSource druidIoHttpSource;
    @Autowired
    private HBaseAPISource hBaseAPISource;

    @Autowired
    private QueryService queryService;
    @Autowired
    private HttpClientService httpClientService;
    @Autowired
    HttpConfig httpConfig;
    @Autowired
    private DatasetService datasetService;
    @Autowired
    private DialectWriter dialectWriter;
    @Autowired
    private ConfigService systemConfigService;
    @Autowired
    private NacosConfig nacosConfig;

    private Environment env;
    private static Pattern pTTL = Pattern.compile(".*toIntervalSecond\\((\\d+)\\)", Pattern.CASE_INSENSITIVE);
    private static final String trafficPort = "8123";
    private final static String DATA_ID = "version.json";

    @Override
    public JSONObject getComponentStatus() {
        JSONObject version = null;
        try {
            String content = systemConfigService.getConfig(DATA_ID, nacosConfig.getGroup(), 3000);
            version = JSONObject.parseObject(content);
        } catch (NacosException e) {
            e.printStackTrace();
        }
        return version;
    }

    @Override
    public BaseResult consistencyCheck() {
        List<Map<String, Object>> dataList = Lists.newArrayList();
        Map<String, Object> logDiff = Maps.newHashMap();
        Map tables = databaseService.getSchemaInfo(MetadataType.TABLES.getValue(), clickHouseHttpSource.getDbName(), false);
        List<String> symbols = (List<String>) tables.get("symbols");
        logDiff.put("logType", SystemServiceImpl.LogType.TRAFFIC_LOGS.getValue());
        Map<String, Map<String, Object>> changeTTL = Maps.newHashMap();
        List<String> ipPorts = getClusterAddressOfCK();
        try {
            for (String ipPort : ipPorts) {
                if (!changeTTL.isEmpty()) {
                    break;
                }
                if (ipPort.startsWith("127.0.0.1")) {
                    String url = clickHouseHttpSource.getUrl();
                    ipPort = url.substring(url.indexOf("//") + 2, url.lastIndexOf(":") + 1).concat(trafficPort);
                }
                for (String tableName : symbols) {
                    Map schemaMap = databaseService.getSchemaInfo("fields", tableName, false);
                    Object tableTTLInSchema = getTableTTLInSchema(schemaMap);
                    Map<String, Object> tableDiff = getTableDiff(ipPort, tableName, tableTTLInSchema);
                    List fieldDiff = getFieldDiff(ipPort, tableName, schemaMap);
                    if (CollectionUtil.isNotEmpty(fieldDiff)) {
                        tableDiff.put("fields", fieldDiff);

                    }
                    if (CollectionUtil.isNotEmpty(tableDiff)) {
                        changeTTL.put(tableName, tableDiff);
                        logDiff.put("tables", Lists.newArrayList(changeTTL));
                        logDiff.put("address", Lists.newArrayList(ipPort));
                    }
                }
            }
        } catch (RuntimeException ex) {
            log.error(" schema TTL check error: {}", ex);
            throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(), ex.getMessage()));
        }
        if (changeTTL.isEmpty()) {
            return BaseResultGenerator.success("ok", dataList);
        }
        dataList.add(logDiff);
        return BaseResultGenerator.success("Log streaming setting task not executed or failed", dataList);
    }

    @Override
    public BaseResult validateMetadata() {
        BaseResult result = null;
        try {
            Stopwatch watch = Stopwatch.createStarted();
            Map<String, Object> statistics = Maps.newLinkedHashMap();
            List<Object> data = Lists.newArrayList();
            checkCKIndexKey(clickHouseHttpSource.getDbName(), databaseService.getAllTable());
            Map<String, Map<String, String>> clickhouseResult = checkMetadataByDatasource(clickHouseHttpSource.getDbName(), databaseService.getAllTable());
            Map<String, Map<String, String>> druidResult = checkMetadataByDatasource(druidIoHttpSource.getDbname(), databaseService.getAllTable());
            Map<String, Map<String, String>> hbaseResult = checkMetadataByDatasource(hBaseAPISource.getDbName(), databaseService.getAllTable());
            Map<String, Map<String, String>> hbase2Result = checkMetadataByDatasource(HBASE_DBNAME2, databaseService.getAllTable());

            data.add(clickhouseResult.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
            data.add(druidResult.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
            data.add(hbaseResult.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
            data.add(hbase2Result.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));

            statistics.put("elapsed", watch.elapsed(TimeUnit.MILLISECONDS));
            result = BaseResultGenerator.success("Metadata Validation Success, Only Table Exist Fields detail.", data, statistics);
            result.setFormatType(OutputMode.JSON.getValue());
        } catch (RuntimeException e) {
            log.error("Metadata Validation Fail: {}", e.getMessage());
            throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(),
                    CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(), e.getMessage()));
        }
        return result;
    }


    @Override
    public BaseResult benchmarkTest(String test, boolean isSaved, String category) {

        BaseResult result = null;
        Map<String, Object> statistics = Maps.newLinkedHashMap();
        Map<String, Map<String, String>> mergeResult;
        Map<String, Map<String, String>> engineResult;
        Map<String, Map<String, String>> clickhouseResult;
        Map<String, Map<String, String>> druidResult;
        Map<String, Map<String, String>> hbaseResult;
        try {
            List<Map<String, Object>> dataSetlist = generateDatasets(category);
            List<String> druidIdList = Lists.newLinkedList();
            List<String> ckIdList = Lists.newLinkedList();
            List<String> engineIdList = Lists.newLinkedList();
            List<String> hbaseIdList = Lists.newLinkedList();

            List<String> druidSqlList = Lists.newLinkedList();
            List<String> ckSqlList = Lists.newLinkedList();
            List<String> engineSqlList = Lists.newLinkedList();
            List<String> hbaseSqlList = Lists.newLinkedList();
            Stopwatch watch = Stopwatch.createStarted();
            if (CollectionUtil.isNotEmpty(dataSetlist)) {
                for (int i = 0; i < dataSetlist.size(); i++) {
                    if (String.valueOf(dataSetlist.get(i).get(DATASET_TYPE)).equalsIgnoreCase("sql")) {
                        String backendEngine = String.valueOf(dataSetlist.get(i).get(BACKEND_ENGINE));
                        String identifierName = String.valueOf(dataSetlist.get(i).get(IDENTIFIER_NAME));
                        String template = String.valueOf(dataSetlist.get(i).get(TEMPLATE));
                        if (DBType.DRUID.getValue().equalsIgnoreCase(backendEngine)) {
                            druidIdList.add(identifierName);
                            druidSqlList.add(template);
                        } else if (DBType.CLICKHOUSE.getValue().equalsIgnoreCase(backendEngine)) {
                            druidIdList.add(identifierName);
                            druidSqlList.add(template);
                        } else if (DBType.HBASE.getValue().equalsIgnoreCase(backendEngine)) {
                            druidIdList.add(identifierName);
                            druidSqlList.add(template);
                        } else {
                            druidIdList.add(identifierName);
                            druidSqlList.add(template);
                        }
                    }
                }
            }

            Optional.of(new File(dialectWriter.buildPocSQL(druidSqlList, DBType.DRUID.getValue(), category)));
            Optional.of(new File(dialectWriter.buildPocSQL(ckSqlList, DBType.CLICKHOUSE.getValue(), category)));
            Optional.of(new File(dialectWriter.buildPocSQL(hbaseSqlList, DBType.HBASE.getValue(), category)));
            Optional.of(new File(dialectWriter.buildPocSQL(engineSqlList, DBType.ENGINE.getValue(), category)));

            clickhouseResult = buildResult(DBType.CLICKHOUSE.getValue(), ckIdList, isSaved, test);
            druidResult = buildResult(DBType.DRUID.getValue(), druidIdList, isSaved, test);
            engineResult = buildResult(DBType.ENGINE.getValue(), engineIdList, isSaved, test);
            hbaseResult = buildResult(DBType.HBASE.getValue(), hbaseIdList, isSaved, test);

            mergeResult = Stream
                    .concat(clickhouseResult.entrySet().stream(), druidResult.entrySet().stream())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            mergeResult = Stream.concat(mergeResult.entrySet().stream(), engineResult.entrySet().stream())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            mergeResult = Stream.concat(mergeResult.entrySet().stream(), hbaseResult.entrySet().stream())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            statistics.put("elapsed", watch.elapsed(TimeUnit.MILLISECONDS));
            result = BaseResultGenerator.success("SQL Queries Success.", mergeResult, statistics);
            result.setFormatType(OutputMode.JSON.getValue());
        } catch (RuntimeException e) {
            log.error("Execute Poc SQL Fail:{}", e);
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(),
                    CommonErrorCode.SQL_EXECUTION_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.SQL_EXECUTION_EXCEPTION.getMessage(), e.getMessage()));
        }
        return result;
    }

    private void checkCKIndexKey(String dbName, List<String> tables) {
        for (String tableName : tables) {
            Schema schema = databaseService.getSchemaByName(tableName);
            if (!dbName.equals(schema.getNamespace())) continue;
            BaseResult indexKeyResult = getBaseResultByEngine(QueryContext.builder().option(QueryOption.REAL_TIME.getValue()).query(String.format(Objects.requireNonNull(env.getProperty("TABLE_INDEX_KEY")), tableName, clickHouseHttpSource.getDbName())).build());
            if (HttpStatusCodeEnum.SUCCESS.getCode() != indexKeyResult.getStatus()) {
                log.error("The Table[" + tableName + "] index key query failed" + indexKeyResult.getMessage());
                throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(),
                        CommonErrorCode.SQL_EXECUTION_EXCEPTION.getCode(),
                        String.format(CommonErrorCode.SQL_EXECUTION_EXCEPTION.getMessage(), " The Table[" + tableName + "] index key query failed"));
            }
            List<Map<String, Object>> dataList = (List<Map<String, Object>>) indexKeyResult.getData();
            List<String> dbIndexKey = StringUtil.isEmpty(dataList.get(0)) ? Lists.newArrayList() : (List<String>) dataList.get(0).get("index_key");
            List<String> schemaIndexKey = databaseService.getIndexKey(tableName);
            if (schemaIndexKey.equals(dbIndexKey)) {
                continue;
            }
            log.error("{} schema index key inconsistent with DB, schema: {}, db: {}", tableName, schemaIndexKey, dbIndexKey);
            throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(),
                    CommonErrorCode.SCHEMA_AND_TABLE_NOT_CONSISTENCY.getCode(),
                    String.format(CommonErrorCode.SCHEMA_AND_TABLE_NOT_CONSISTENCY.getMessage(),
                            String.format("%s schema index key is: %s, db index key is: %s", tableName, schemaIndexKey, dbIndexKey)));
        }
    }

    private BaseResult getBaseResultByEngine(QueryContext queryProfile) {
        return queryService.executeQuery(queryProfile);
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
            Schema schema = databaseService.getSchemaByName(tableName);
            if (!dbName.equals(schema.getNamespace())) continue;
            BaseResult baseResult = getBaseResultByEngine(QueryContext.builder().option(QueryOption.REAL_TIME.getValue()).query("describe " + tableName).build());
            if (HttpStatusCodeEnum.SUCCESS.getCode() != baseResult.getStatus()) {
                log.error("The Table[" + tableName + "] Structure query failed" + baseResult.getMessage());
                throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(),
                        CommonErrorCode.SQL_EXECUTION_EXCEPTION.getCode(),
                        String.format(CommonErrorCode.SQL_EXECUTION_EXCEPTION.getMessage(), " The Table[" + tableName + "] Structure query failed"));
            }
            List<Map<String, Object>> dbFields = JsonPath.read(JSON.toJSONString(baseResult.getData()), "$.fields.*");

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
                log.error("Schema Fields greater than Table Fields,Schema " + tableName + " exist fields :" + entriesOnlyOnLeft);
                throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(),
                        CommonErrorCode.SQL_EXECUTION_EXCEPTION.getCode(),
                        String.format(CommonErrorCode.SQL_EXECUTION_EXCEPTION.getMessage(),
                                " Schema Fields greater than Table Fields,Schema " + tableName + " exist fields " + entriesOnlyOnLeft));

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

    private Object getTableTTLInSchema(Map schemaMap) {
        Object tableTTLInSchema = null;
        if (StringUtil.isNotEmpty(schemaMap)) {
            if (schemaMap.containsKey("doc")) {
                Map<String, Object> schemaHasDoc = (Map<String, Object>) schemaMap.get("doc");
                if (schemaHasDoc.containsKey("ttl")) {
                    tableTTLInSchema = StringUtil.isEmpty(schemaHasDoc.get("ttl")) ? null : schemaHasDoc.get("ttl").toString();
                }
            }
        }
        return tableTTLInSchema;
    }

    private Map<String, Object> getTableDiff(String ipPort, String tableName, Object tableTTLInSchema) {
        String sql = String.format(Objects.requireNonNull(env.getProperty("TABLE_TTL")), tableName, clickHouseHttpSource.getDbName());
        Map<String, String> result = executeHttpGetOfCK(ipPort, sql, clickHouseHttpSource.getSystemDBName());
        if (Integer.parseInt(result.get("status")) != HttpStatusCodeEnum.SUCCESS.getCode()) {
            log.error(" query table TTL error: {}", result.toString());
            throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.SQL_EXECUTION_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.SQL_EXECUTION_EXCEPTION.getMessage(), result.toString()));
        }
        Object tableTTLInDB = null;
        Map<String, Object> tableDiff = Maps.newHashMap();
        Map o = (Map) JSON.parseObject(result.get("result"), Map.class);
        Map<String, Object> resultMap = (Map<String, Object>) o;
        for (Map<String, Object> datum : (List<Map<String, Object>>) resultMap.get("data")) {
            Matcher matcher = pTTL.matcher(String.valueOf(datum.get("table_ttl")));
            tableTTLInDB = matcher.find() ? matcher.group(1) : null;
        }
        if (!String.valueOf(tableTTLInDB).equals(String.valueOf(tableTTLInSchema))) {
            tableDiff.put("last_ttl", StringUtil.isEmpty(tableTTLInSchema) ? null : tableTTLInSchema.toString());
            tableDiff.put("used_ttl", StringUtil.isEmpty(tableTTLInDB) ? null : tableTTLInDB.toString());
            log.warn("table ttl: {} not consistency.", tableName);
        }
        return tableDiff;
    }

    private List<Map<String, Object>> getFieldDiff(String ipPort, String
            tableName, Map<String, Object> schemaMap) {
        List<Map<String, Object>> fieldDiff = Lists.newArrayList();
        Map<String, String> result = executeHttpGetOfCK(ipPort, "describe " + tableName + "_local", clickHouseHttpSource.getDbName());
        if (Integer.parseInt(result.get("status")) != HttpStatusCodeEnum.SUCCESS.getCode()) {
            log.error(" query table field TTL error: {}", result.toString());
            throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.SQL_EXECUTION_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.SQL_EXECUTION_EXCEPTION.getMessage(), result.toString()));
        }
        List<Map<String, Object>> fields = (List<Map<String, Object>>) schemaMap.get("fields");
        for (Map<String, Object> field : fields) {
            Object read = JsonPath.read(result, "$.result");
            List<Object> fieldList = JsonPath.read(JSONUtil.toJsonStr(JSONUtil.parseObj(read)), "$.data[?(@.name == \"" + field.get("name") + "\")].ttl_expression");
            Object fieldTTLInSchema = null;
            Object fieldTTLInDB;
            if (StringUtil.isNotEmpty(field.get("doc"))) {
                Map<String, Object> doc = (Map<String, Object>) field.get("doc");
                fieldTTLInSchema = StringUtil.isEmpty(doc.get("ttl")) ? null : doc.get("ttl");
            }
            Matcher matcher = pTTL.matcher(String.valueOf(fieldList.get(0)));
            fieldTTLInDB = matcher.find() ? matcher.group(1) : null;
            if (String.valueOf(fieldTTLInSchema).equals(String.valueOf(fieldTTLInDB))) {
                continue;
            }
            log.warn("field ttl: address {} {}-{} not consistency.", ipPort, tableName, field.get("name"));
            Map<String, Object> ttlDiff = Maps.newHashMap();
            ttlDiff.put("last_ttl", StringUtil.isEmpty(fieldTTLInSchema) ? null : fieldTTLInSchema.toString());
            ttlDiff.put("used_ttl", StringUtil.isEmpty(fieldTTLInDB) ? null : fieldTTLInDB.toString());
            if (fieldDiff.stream().noneMatch(o -> o.containsKey(field.get("name").toString()))) {
                Map<String, Object> item = Maps.newHashMap();
                item.put(field.get("name").toString(), ttlDiff);
                fieldDiff.add(item);
            }
        }
        return fieldDiff;
    }


    private List<String> getClusterAddressOfCK() {
        List<String> endpointList = new ArrayList<>();
        BaseResult baseResult = queryService.executeQuery(QueryContext.builder().query("SELECT DISTINCT concat(host_address,':','" + trafficPort + "') as endpoint FROM clusters where cluster = 'ck_cluster'").build());
        if (baseResult.isSuccess()) {
            List<Map<String, Object>> data = (List<Map<String, Object>>) baseResult.getData();
            for (Map<String, Object> datum : data) {
                endpointList.add(String.valueOf(datum.get("endpoint")));
            }
        }
        return endpointList;
    }

    private Map<String, String> executeHttpGetOfCK(String ipPort, String sql, String dbName) {
        String queryURL = URLUtil.normalize("http://" + ipPort + "/?");
        StringBuilder paramBuilder = new StringBuilder("user=")
                .append(clickHouseHttpSource.getRealTimeAccountUserName()).append("&")
                .append("password=").append(clickHouseHttpSource.getRealTimeAccountPin()).append("&")
                .append("database=").append(dbName).append("&")
                .append("query=").append(sql)
                .append(" FORMAT JSON;");
        List<NameValuePair> values = URLEncodedUtils.parse(paramBuilder.toString(), StandardCharsets.UTF_8);
        int socketTimeOut = httpConfig.getCkRealTimeAccountSocketTimeOut();
        return httpClientService.httpGet(queryURL + URLEncodedUtils.format(values, "utf-8"), socketTimeOut);
    }

    private List<Map<String, Object>> generateDatasets(String category) {
        List<String> categorylist = Lists.newLinkedList();
        List<Record> list = Db.find(String.format(Objects.requireNonNull(env.getProperty("SQL_DATASETS_CATEGORY"))));
        list.forEach(record -> categorylist.add(String.valueOf(record.getColumns().get("category"))));
        if (CollectionUtil.isNotEmpty(categorylist) && StrUtil.isNotBlank(category) && !categorylist.contains(category)) {
            throw new QGWBusinessException(HttpStatus.HTTP_BAD_REQUEST, CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.DATASET_CATEGORY_ERROR));
        }
        return datasetService.getDatasets(Lists.newArrayList(), category, null);
    }

    private Map<String, Map<String, String>> buildResult(String executeEngine, List<String> list,
                                                         boolean isSaved, String test) {
        Map<String, Map<String, String>> resultMap = Maps.newLinkedHashMap();
        Map<String, String> infoMap = Maps.newLinkedHashMap();
        FileWriter writer = null;
        String benchFileName = executeEngine + "_queries_" + DateUtils.getCurrentDate(DateUtils.YYYYMMDD) + ".sql";
        if (isSaved && BenchmarkOption.SQL_EXECUTION.getValue().equalsIgnoreCase(test)) {
            File file = new File("benchmark" + File.separator + benchFileName + "." + IdUtil.simpleUUID() + ".dat");
            writer = FileWriter.create(file);
        }
        int index = 0;
        for (String line : list) {
            index++;
            Stopwatch watch = Stopwatch.createStarted();
            BaseResult queryResult = datasetService.getPreview(line);
            if (StringUtil.isEmpty(queryResult.getStatistics())) {
                Map<String, Object> statistics = Maps.newLinkedHashMap();
                statistics.put("elapsed", watch.elapsed(TimeUnit.MILLISECONDS));
                queryResult.setStatistics(statistics);
            }
            processQueryResult(benchFileName, infoMap, writer, index, queryResult);
        }
        resultMap.put(benchFileName, buildQueryTimeMetric(infoMap));
        return resultMap;
    }

    private Map<String, String> buildQueryTimeMetric(Map<String, String> infoMap) {
        if (StringUtil.isNotEmpty(infoMap)) {
            double[] values = infoMap.values().stream().mapToDouble(value -> Double.valueOf(value)).sorted().toArray();
            infoMap.put("Min", String.valueOf(CommonUtil.round(StatUtils.min(values), 2)));
            infoMap.put("Mean", String.valueOf(CommonUtil.round(StatUtils.mean(values), 2)));
            infoMap.put("Median", String.valueOf(CommonUtil.round(StatUtils.percentile(values, 50), 2)));
            infoMap.put("P95", String.valueOf(CommonUtil.round(StatUtils.percentile(values, 95), 2)));
            infoMap.put("P99", String.valueOf(CommonUtil.round(StatUtils.percentile(values, 99), 2)));
            infoMap.put("MAX", String.valueOf(CommonUtil.round(StatUtils.max(values), 2)));
        }
        return infoMap;
    }

    private void processQueryResult(String name, Map<String, String> infoMap, FileWriter writer,
                                    int index, BaseResult queryResult) {
        int statusCode = Integer.parseInt(queryResult.getStatus().toString());
        if (statusCode == HttpStatusCodeEnum.SUCCESS.getCode()) {
            if (writer != null) {
                writer.append("--Query" + index + "\t" + "Elapsed(ms):" + queryResult.getStatistics().get("elapsed") + "\n");
                writer.append(queryResult.getMeta() + "\n");
                writer.append(queryResult.getData() + "\n");
            }
            infoMap.put("Query" + index, queryResult.getStatistics().get("elapsed") + "");
        } else if (statusCode == HttpStatusCodeEnum.GATEWAY_TIMEOUT.getCode() || statusCode == HttpStatusCodeEnum.BAD_GATEWAY.getCode()) {
            infoMap.put("Query" + index, "status:" + queryResult.getStatus() + ",message:" + queryResult.getMessage());

        } else {
            throw new QGWBusinessException(statusCode, CommonErrorCode.SQL_EXECUTION_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.SQL_EXECUTION_EXCEPTION.getMessage(), name
                            + ",Query" + index + "," + queryResult.getStatus() + "," + queryResult.getMessage()));
        }
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.env = environment;
    }
}
