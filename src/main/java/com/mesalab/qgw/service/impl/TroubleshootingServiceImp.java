package com.mesalab.qgw.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONPath;
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
import com.mesalab.common.entity.DataTypeMapping;
import com.mesalab.common.enums.*;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.nacos.NacosConfig;
import com.mesalab.common.utils.JsonSchemaValidator;
import com.mesalab.qgw.benchmark.DialectWriter;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.qgw.model.basic.*;
import com.mesalab.qgw.service.*;
import com.opencsv.CSVWriter;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
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
    private static final String BACKEND_ENGINE = "backend_engine";
    private static final String IDENTIFIER_NAME = "identifier_name";
    private static final String TEMPLATE = "template";
    private static final String TYPE = "type";

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
    private SQLSyncQueryService sqlSyncQueryService;
    @Autowired
    private HttpClientService httpClientService;
    @Autowired
    private HttpClientServiceV2 httpClientServiceV2;
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
    @Autowired
    private QueryJobService queryJobService;
    @Value("${server.port}")
    private int serverPort;
    @Value("${management.endpoints.web.base-path}")
    private String basePath;
    @Value("${management.endpoints.web.path-mapping.health}")
    private String healthPath;

    private Environment env;
    private static Pattern pTTL = Pattern.compile(".*toIntervalSecond\\((\\d+)\\)", Pattern.CASE_INSENSITIVE);
    private static final String trafficPort = "8123";
    private final static String DATA_ID = "version.json";
    private final static String PUBLIC_SCHEMA_INFO = "public_schema_info.json";
    private static final String FIELDS = "fields";
    private static final String NAMESPACE = "namespace";
    private static final String SCHEMA_SYNTAX_RULE = "schema-syntax-validation.json";

    private static String localHostAddress;
    private final static Map<String, String> metaMap = Maps.newHashMap();

    static {
        localHostAddress = NetUtil.getLocalhostStr();
    }

    static {
        metaMap.put("Int8", DataTypeMapping.INT);
        metaMap.put("Int16", DataTypeMapping.INT);
        metaMap.put("Int32", DataTypeMapping.INT);
        metaMap.put("Int64", DataTypeMapping.LONG);
        metaMap.put("Int128", DataTypeMapping.LONG);
        metaMap.put("Int256", DataTypeMapping.LONG);
        metaMap.put("UInt8", DataTypeMapping.INT);
        metaMap.put("UInt16", DataTypeMapping.INT);
        metaMap.put("UInt32", DataTypeMapping.LONG);
        metaMap.put("UInt64", DataTypeMapping.LONG);
        metaMap.put("UInt256", DataTypeMapping.LONG);
        metaMap.put("Nullable(Int32)", DataTypeMapping.INT);
        metaMap.put("Nullable(Int64)", DataTypeMapping.LONG);
        metaMap.put("Nullable(Float64)", DataTypeMapping.DOUBLE);
        metaMap.put("BIGINT", DataTypeMapping.LONG);
        metaMap.put("Float32", DataTypeMapping.FLOAT);
        metaMap.put("Float64", DataTypeMapping.DOUBLE);
        metaMap.put("Date", DataTypeMapping.DATE);
        metaMap.put("DateTime", DataTypeMapping.TIMESTAMP);
        metaMap.put("DateTime64(3)", DataTypeMapping.STRING);
        metaMap.put("TIMESTAMP", DataTypeMapping.STRING);
        metaMap.put("Array(Int64)", DataTypeMapping.ARRAY.concat("(").concat(DataTypeMapping.LONG).concat(")"));
        metaMap.put("Array(Int32)", DataTypeMapping.ARRAY.concat("(").concat(DataTypeMapping.INT).concat(")"));
        metaMap.put("Array(String)", DataTypeMapping.ARRAY.concat("(").concat(DataTypeMapping.STRING).concat(")"));
        metaMap.put("VARCHAR", DataTypeMapping.STRING);
        metaMap.put("LowCardinality(String)", DataTypeMapping.STRING);
    }

    @Override
    public JSONObject getComponentStatus() {
        Map<String, Object> jsonMap = Maps.newLinkedHashMap();
        try {
            String content = systemConfigService.getConfig(DATA_ID, nacosConfig.getGroup(), 3000);
            Map componentInfo = JSON.parseObject(content, Map.class);
            List<Map<String, Object>> componentList = (List<Map<String, Object>>) componentInfo.get("components");
            Map<String, Object> componentHealthResult = getComponentHealth();
            Set<String> componentNameSet = componentHealthResult.keySet();
            boolean isStatusUp = true;
            for (Map component : componentList) {
                String componentName = StrUtil.removeAll(String.valueOf(component.get("name")), " ");
                if (componentNameSet.contains(componentName)) {
                    Map<String, String> healthMap = buildHealth(componentName, componentHealthResult);
                    if (healthMap.get("status").equals("DOWN")) {
                        isStatusUp = false;
                    }
                    if (StringUtil.isNotEmpty(healthMap.get("version"))) {
                        component.put("version", healthMap.get("version"));
                        healthMap.remove("version");
                    }
                    component.put("health", healthMap);
                }
            }
            jsonMap.put("product", componentInfo.get("product"));
            jsonMap.put("status", isStatusUp ? "UP" : "DOWN");
            jsonMap.put("components", componentList);
            jsonMap.put("registered", componentInfo.get("registered"));
            jsonMap.put("version", componentInfo.get("version"));
            jsonMap.put("updated", componentInfo.get("updated"));
        } catch (NacosException e) {
            log.error("get Component Status error:{}", e.getMessage());
        }
        return new JSONObject(jsonMap);
    }

    @Override
    public BaseResult consistencyCheck() {
        List<Map<String, Object>> dataList = Lists.newArrayList();
        Map<String, Object> logDiff = Maps.newHashMap();
        Map tables = databaseService.getSchemaInfo(MetadataType.TABLES.getValue(), clickHouseHttpSource.getDbName(), false);
        List<String> symbols = (List<String>) tables.get("symbols");
        logDiff.put("logType", DatabaseServiceImpl.LogType.TRAFFIC_LOGS.getValue());
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
                    Map schemaMap = databaseService.getSchemaInfo(FIELDS, tableName, false);
                    Object tableTTLInSchema = getTableTTLInSchema(schemaMap);
                    Map<String, Object> tableDiff = getTableDiff(ipPort, tableName, tableTTLInSchema);
                    List fieldDiff = getFieldDiff(ipPort, tableName, schemaMap);
                    if (CollectionUtil.isNotEmpty(fieldDiff)) {
                        tableDiff.put(FIELDS, fieldDiff);

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
            Map<String, Map<String, List<String>>> resultData = Maps.newHashMap();
            JsonSchemaValidator jsonSchemaValidator = JsonSchemaValidator.getInstance();
            for (String tableName : databaseService.getAllTable()) {
                Map schemaMap = databaseService.getSchemaInfo(FIELDS, tableName, false);
                if (!clickHouseHttpSource.getDbName().equals(schemaMap.get(NAMESPACE)) && !druidIoHttpSource.getDbname().equals(schemaMap.get(NAMESPACE)))
                    continue;
                jsonSchemaValidator
                        .addRule(SCHEMA_SYNTAX_RULE, "schema")
                        .validateSchema(JSON.toJSONString(schemaMap));
            }
            checkCKIndexKey(clickHouseHttpSource.getDbName(), databaseService.getAllTable());
            checkMetadataFieldAndType(databaseService.getAllTable(), resultData);
            checkMetadataFunctionConstraint(databaseService.getAllTable());

            statistics.put("elapsed", watch.elapsed(TimeUnit.MILLISECONDS));
            result = BaseResultGenerator.success("Json Schema Validation Success.", resultData, statistics);
            result.setOutputMode(OutputMode.JSON.getValue());
        } catch (RuntimeException e) {
            log.error("Json Schema Validation Fail: {}", e.getMessage());
            throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(),
                    CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(), e.getMessage()));
        }
        return result;
    }


    @Override
    public BaseResult datesetVerification() {
        List<Record> datasetList = Db.find(String.format(Objects.requireNonNull(env.getProperty("SQL_DATASETS")), ""));
        List<Map<String, String>> sqlDatasetList = Lists.newArrayList();
        List<Map<String, String>> dslDatasetList = Lists.newArrayList();
        List<Map<String, String>> resultData = Lists.newArrayList();
        List<LinkedHashMap> variables = datasetService.getVariable();
        datasetList.forEach(record -> {
            Map<String, String> map = Maps.newHashMap();
            map.put(IDENTIFIER_NAME, String.valueOf(record.getColumns().get(IDENTIFIER_NAME)));
            map.put(TEMPLATE, String.valueOf(datasetService.buildExecSQL(variables, String.valueOf(record.getColumns().get(TEMPLATE)))));
            if (record.getColumns().get(DATASET_TYPE).equals("sql")) {
                sqlDatasetList.add(map);
            } else if (record.getColumns().get(DATASET_TYPE).equals("dsl")) {
                dslDatasetList.add(map);
            }
        });

        SqlQueryRequestParam sqlQueryRequest;
        for (Map sqlDataset : sqlDatasetList) {
            Map<String, String> map = Maps.newHashMap();
            try {
                sqlQueryRequest = JSON.parseObject(String.valueOf(sqlDataset.get(TEMPLATE)), SqlQueryRequestParam.class);
                sqlQueryRequest.setExecutionMode(ExecutionMode.ONESHOT.getValue());
                sqlQueryRequest.setIsDryRun(1);
                sqlQueryRequest.setOutputMode(OutputMode.JSON.getValue());
                log.info("SQL Dataset Verification, ID is: {}", sqlDataset.get(IDENTIFIER_NAME));
                queryJobService.createSQLAdHocQuery(sqlQueryRequest);
            } catch (QGWBusinessException | BusinessException e) {
                map.put(String.valueOf(sqlDataset.get(IDENTIFIER_NAME)), e.getMessage());
                resultData.add(map);
                log.error("SQL Dataset execute error, ID is: {}, message is: {}", sqlDataset.get(IDENTIFIER_NAME), e.getMessage());
            }
        }

        DSLQueryRequestParam dslQueryRequest;
        for (Map dslDataset : dslDatasetList) {
            Map<String, String> map = Maps.newHashMap();
            try {
                dslQueryRequest = JSON.parseObject(String.valueOf(dslDataset.get(TEMPLATE)), DSLQueryRequestParam.class);
                dslQueryRequest.setExecutionMode(ExecutionMode.ONESHOT.getValue());
                dslQueryRequest.setIsDryRun(1);
                dslQueryRequest.setOutputMode(OutputMode.JSON.getValue());
                log.info("DSL Dataset Verification, ID is {}", dslDataset.get(IDENTIFIER_NAME));
                queryJobService.createDSLAdHocQuery(dslQueryRequest);
            } catch (RuntimeException e) {
                map.put(String.valueOf(dslDataset.get(IDENTIFIER_NAME)), e.getMessage());
                resultData.add(map);
                log.error("DSL Dataset execute error, ID is: {}, message is: {}", dslDataset.get(IDENTIFIER_NAME), e.getMessage());
            }
        }

        return buildResult(variables, resultData, sqlDatasetList.size() + dslDatasetList.size());
    }

    @Override
    public BaseResult benchmarkTest(String test, boolean isSaved) {

        BaseResult result = null;
        Map<String, Object> dataResult = Maps.newLinkedHashMap();
        Map<String, Object> statistics = Maps.newLinkedHashMap();
        Map<String, Map<String, String>> mergeResult;
        Map<String, Map<String, String>> engineResult;
        Map<String, Map<String, String>> clickhouseResult;
        Map<String, Map<String, String>> druidResult;
        Map<String, Map<String, String>> hbaseResult;
        try {
            List<Map<String, Object>> dataSetlist = datasetService.getDatasets(Lists.newArrayList(), test, null);
            List<Map> druidDatasetList = Lists.newLinkedList();
            List<Map> ckDatasetList = Lists.newLinkedList();
            List<Map> engineDatasetList = Lists.newLinkedList();
            List<Map> hbaseDatasetList = Lists.newLinkedList();
            List<LinkedHashMap> variables = datasetService.getVariable();

            Stopwatch watch = Stopwatch.createStarted();
            if (CollectionUtil.isNotEmpty(dataSetlist)) {
                for (int i = 0; i < dataSetlist.size(); i++) {
                    String backendEngine = String.valueOf(dataSetlist.get(i).get(BACKEND_ENGINE));
                    String identifierName = String.valueOf(dataSetlist.get(i).get(IDENTIFIER_NAME));
                    String template = String.valueOf(dataSetlist.get(i).get(TEMPLATE));
                    String type = String.valueOf(dataSetlist.get(i).get(TYPE));
                    Map<String, String> map = Maps.newHashMap();
                    map.put(IDENTIFIER_NAME, identifierName);
                    map.put(TYPE, type);
                    map.put(TEMPLATE, String.valueOf(datasetService.buildExecSQL(variables, template)));
                    if (DBEngineType.DRUID.getValue().equalsIgnoreCase(backendEngine)) {
                        druidDatasetList.add(map);
                    } else if (DBEngineType.CLICKHOUSE.getValue().equalsIgnoreCase(backendEngine)) {
                        ckDatasetList.add(map);
                    } else if (DBEngineType.HBASE.getValue().equalsIgnoreCase(backendEngine)) {
                        hbaseDatasetList.add(map);
                    } else {
                        engineDatasetList.add(map);
                    }
                }
            } else {
                return BaseResultGenerator.failure(HttpStatusCodeEnum.BAD_REQUEST.getCode(), "The dataset category does not exist.");
            }

            clickhouseResult = buildResult(test, DBEngineType.CLICKHOUSE.getValue(), ckDatasetList, isSaved);
            druidResult = buildResult(test, DBEngineType.DRUID.getValue(), druidDatasetList, isSaved);
            engineResult = buildResult(test, DBEngineType.QGW.getValue(), engineDatasetList, isSaved);
            hbaseResult = buildResult(test, DBEngineType.HBASE.getValue(), hbaseDatasetList, isSaved);

            mergeResult = Stream
                    .concat(clickhouseResult.entrySet().stream(), druidResult.entrySet().stream())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            mergeResult = Stream.concat(mergeResult.entrySet().stream(), engineResult.entrySet().stream())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            mergeResult = Stream.concat(mergeResult.entrySet().stream(), hbaseResult.entrySet().stream())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            dataResult.put(test, mergeResult);
            statistics.put("elapsed", watch.elapsed(TimeUnit.MILLISECONDS));
            result = BaseResultGenerator.success(StrUtil.format("SQL benchmark complete, time range is: {}", getTimeRangeByVariable(variables)), dataResult, statistics);
            result.setOutputMode(OutputMode.JSON.getValue());
        } catch (RuntimeException e) {
            log.error("Execute Poc SQL Fail:{}", e);
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(),
                    CommonErrorCode.SQL_EXECUTION_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.SQL_EXECUTION_EXCEPTION.getMessage(), e.getMessage()));
        }
        return result;
    }

    private Map<String, Object> getComponentHealth() {
        String healthCheckUrl = URLUtil.normalize(localHostAddress + ":" + serverPort + basePath + healthPath);
        HttpResponseResult result = httpClientServiceV2.get(healthCheckUrl, httpConfig.getServerRequestTimeOut());
        Map<String, Object> componentsHealthResult = JSON.parseObject(result.getResponseBody(), Map.class);
        Map<String, Object> componentMap = (Map<String, Object>) componentsHealthResult.get("components");
        return componentMap;
    }


    private Map<String, String> buildHealth(String componentName, Map<String, Object> map) {
        Map<String, Object> componentMap = (Map<String, Object>) map.get(componentName);
        Map<String, String> healthMap = (Map<String, String>) componentMap.get("details");
        healthMap.put("status", String.valueOf(componentMap.get("status")));
        return healthMap;
    }

    private void checkCKIndexKey(String dbName, List<String> tables) {
        for (String tableName : tables) {
            LinkedHashMap<String, Object> schemaMap = databaseService.getSchemaByName(tableName);
            if (!dbName.equals(schemaMap.get("namespace"))) continue;
            BaseResult indexKeyResult = getBaseResultByEngine(SQLQueryContext.builder().option(QueryOption.REAL_TIME.getValue()).originalSQL(String.format(Objects.requireNonNull(env.getProperty("TABLE_INDEX_KEY")), tableName, clickHouseHttpSource.getDbName())).build());
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

    private BaseResult getBaseResultByEngine(SQLQueryContext queryProfile) {
        return sqlSyncQueryService.executeQuery(queryProfile);
    }

    /**
     * 对指定数据源dbName下的schema 与数据库的字段及字段类型进行对比检查
     *
     * @param
     * @param tables
     * @return
     */
    private void checkMetadataFieldAndType(List<String> tables, Map<String, Map<String, List<String>>> resultData) {
        for (String tableName : tables) {
            Map schemaMap = databaseService.getSchemaInfo(FIELDS, tableName, false);
            String dbName = String.valueOf(schemaMap.get(NAMESPACE));
            if (!clickHouseHttpSource.getDbName().equals(dbName)
                    && !druidIoHttpSource.getDbname().equals(dbName)
                    && !hBaseAPISource.getDbName().equals(dbName)) continue;
            BaseResult baseResult = getBaseResultByEngine(SQLQueryContext.builder().option(QueryOption.REAL_TIME.getValue()).originalSQL("describe " + tableName).build());
            if (HttpStatusCodeEnum.SUCCESS.getCode() != baseResult.getStatus()) {
                log.error("The Table[" + tableName + "] Structure query failed" + baseResult.getMessage());
                throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(),
                        CommonErrorCode.SQL_EXECUTION_EXCEPTION.getCode(),
                        String.format(CommonErrorCode.SQL_EXECUTION_EXCEPTION.getMessage(), " The Table[" + tableName + "] Structure query failed"));
            }
            List<Map<String, Object>> dbFields = JsonPath.read(JSON.toJSONString(baseResult.getData()), "$.fields.*");
            Map<String, Object> dbFieldMap = buildDBFiedldMap(dbFields);
            List<Map<String, Object>> schemaFields = (List<Map<String, Object>>) schemaMap.get(FIELDS);
            Map<String, Object> schemaFieldMap = buildSchemaFiedldMap(schemaFields);

            MapDifference<String, Object> difference = Maps.difference(schemaFieldMap, dbFieldMap);
            Map<String, Object> entriesOnlyOnLeft = difference.entriesOnlyOnLeft();
            if (entriesOnlyOnLeft.size() > 0) {
                log.error("Schema Fields greater than Table Fields,Schema " + tableName + " exist fields :" + entriesOnlyOnLeft);
                throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(),
                        CommonErrorCode.SQL_EXECUTION_EXCEPTION.getCode(),
                        String.format(CommonErrorCode.SQL_EXECUTION_EXCEPTION.getMessage(),
                                "Schema Fields greater than Table Fields,Schema " + tableName + " exist fields " + entriesOnlyOnLeft));
            }

            Map<String, Object> entriesOnlyOnRight = difference.entriesOnlyOnRight();
            String fieldValidationMessage = "Schema Fields less than Table Fields:" + entriesOnlyOnRight;
            if (entriesOnlyOnRight.size() > 0) {
                log.warn(tableName + " " + fieldValidationMessage);
                buildData(resultData, tableName, dbName, fieldValidationMessage);
            }

            Map<String, MapDifference.ValueDifference<Object>> fieldTypeDiff = difference.entriesDiffering();
            if (fieldTypeDiff.size() > 0 && (clickHouseHttpSource.getDbName().equals(dbName)
                    || druidIoHttpSource.getDbname().equals(dbName))) {
                StringBuilder sb = new StringBuilder();
                for (String field : fieldTypeDiff.keySet()) {
                    sb.append(field)
                            .append("[")
                            .append(fieldTypeDiff.get(field).leftValue())
                            .append(" modified to ")
                            .append(fieldTypeDiff.get(field).rightValue())
                            .append("]")
                            .append(", ");
                }
                sb.setLength(sb.length() - 2);
                throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(),
                        CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                        String.format(QGWMessageConst.SCHEMA_FIELD_TYPE_NEED_CHECK, tableName, sb));
            }
        }
    }

    private static Map<String, Object> buildDBFiedldMap(List<Map<String, Object>> dbFields) {
        Map<String, Object> dbFieldMap = Maps.newHashMap();
        dbFields.stream().forEach(objectMap -> {
            String type = StrUtil.isBlank(metaMap.get(objectMap.get("type"))) ? "string" : metaMap.get(objectMap.get("type"));
            dbFieldMap.put(String.valueOf(objectMap.get("name")), type);
        });
        return dbFieldMap;
    }

    private static Map<String, Object> buildSchemaFiedldMap(List<Map<String, Object>> schemaFields) {
        Map<String, Object> schemaFieldMap = Maps.newHashMap();
        schemaFields.stream().forEach(objectMap -> {
            String type;
            if (objectMap.get("type") instanceof String) {
                type = String.valueOf(objectMap.get("type"));
            } else {
                Map map = (Map) objectMap.get("type");
                type = String.valueOf(map.get("type")).equals("array") ? String.valueOf(map.get("type")).concat("(").concat(String.valueOf(map.get("items"))).concat(")") : String.valueOf(map.get("type"));
            }
            schemaFieldMap.put(String.valueOf(objectMap.get("name")), type);
        });
        return schemaFieldMap;
    }

    private static void buildData(Map<String, Map<String, List<String>>> resultData, String tableName, String dbName, String message) {
        if (!resultData.isEmpty()) {
            if (resultData.containsKey(dbName)) {
                if (resultData.get(dbName).containsKey(tableName)) {
                    Map<String, List<String>> map = resultData.get(dbName);
                    map.get(tableName).add(message);
                } else {
                    Map<String, List<String>> map = resultData.get(dbName);
                    List<String> list = Lists.newArrayList();
                    list.add(message);
                    map.put(tableName, list);
                    resultData.put(dbName, map);
                }
            } else {
                putData(resultData, tableName, dbName, message);
            }
        } else {
            putData(resultData, tableName, dbName, message);
        }
    }

    private static void putData(Map<String, Map<String, List<String>>> resultData, String tableName, String dbName, String message) {
        Map<String, List<String>> map = Maps.newHashMap();
        List<String> list = Lists.newArrayList();
        list.add(message);
        map.put(tableName, list);
        resultData.put(dbName, map);
    }

    private void checkMetadataFunctionConstraint(List<String> tables) {
        Object cfg = databaseService.getCfg(PUBLIC_SCHEMA_INFO);
        Map map = JSON.parseObject(String.valueOf(JSON.toJSON(cfg)), Map.class);
        List<String> aggregationFunctions = JsonPath.read(map, "$.functions.aggregation[*].name");
        for (String tableName : tables) {
            Map schemaMap = databaseService.getSchemaInfo(FIELDS, tableName, false);
            String dbName = String.valueOf(schemaMap.get(NAMESPACE));
            if (!clickHouseHttpSource.getDbName().equals(dbName) && !druidIoHttpSource.getDbname().equals(dbName))
                continue;
            List<Map<String, Object>> schemaFields = (List<Map<String, Object>>) schemaMap.get(FIELDS);
            schemaFields.stream().forEach(objectMap -> {
                if (JSONPath.contains(objectMap, "$.doc.constraints.aggregation_functions")) {
                    String str = JsonPath.read(objectMap, "$.doc.constraints.aggregation_functions");
                    String funStr = str.replaceAll(" ", "");
                    List<String> funList = new ArrayList<>(Arrays.asList(funStr.split(",")));
                    for (String fun : funList) {
                        if (!aggregationFunctions.contains(fun)) {
                            throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(),
                                    CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                                    String.format(QGWMessageConst.SCHEMA_FUNCTION_CONSTRAINT_ERROR, tableName, objectMap.get("name"), fun));
                        }
                    }
                }
            });
        }
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
            tableDiff.put("last_ttl", StringUtil.isEmpty(tableTTLInSchema) ? null : String.valueOf(tableTTLInSchema));
            tableDiff.put("used_ttl", StringUtil.isEmpty(tableTTLInDB) ? null : String.valueOf(tableTTLInDB));
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
        List<Map<String, Object>> fields = (List<Map<String, Object>>) schemaMap.get(FIELDS);
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
            ttlDiff.put("last_ttl", StringUtil.isEmpty(fieldTTLInSchema) ? null : String.valueOf(fieldTTLInSchema));
            ttlDiff.put("used_ttl", StringUtil.isEmpty(fieldTTLInDB) ? null : String.valueOf(fieldTTLInDB));
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
        BaseResult baseResult = sqlSyncQueryService.executeQuery(SQLQueryContext.builder().originalSQL("SELECT DISTINCT concat(host_address,':','" + trafficPort + "') as endpoint FROM clusters where cluster = 'ck_cluster'").build());
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

    private BaseResult<List<Map<String, String>>> buildResult(List<LinkedHashMap> variables, List<Map<String, String>> resultData, int totalSize) {
        if (CollectionUtil.isEmpty(resultData)) {
            return BaseResultGenerator.success(StrUtil.format("There are {} datasets in total, and 100% of them pass the verification, time range is: {}", totalSize, getTimeRangeByVariable(variables)), resultData);
        } else {
            return BaseResultGenerator.failure(HttpStatusCodeEnum.SERVER_ERROR.getCode(), StrUtil.format("There are {} datasets in total, and {} of them pass the verification, time range is: {}", totalSize, NumberUtil.decimalFormat("#.##%", ((totalSize - resultData.size()) * 1.00 / totalSize)), getTimeRangeByVariable(variables)), resultData);
        }
    }

    private Map<String, Map<String, String>> buildResult(String test, String executeEngine, List<Map> list, boolean isSaved) {
        Map<String, Map<String, String>> resultMap = Maps.newLinkedHashMap();
        Map<String, String> infoMap = Maps.newLinkedHashMap();
        CSVWriter csvWriter = null;
        String benchFileName = executeEngine + "_queries_" + DateUtils.getCurrentDate(DateUtils.YYYYMMDD);
        if (isSaved) {
            try {
                String directoryPath = new File("").getCanonicalPath() + File.separator + "benchmark";
                File directory = new File(directoryPath);
                if (!directory.exists()) {
                    directory.mkdir();
                }
                String filePath = directoryPath + File.separator + benchFileName + "." + IdUtil.simpleUUID() + ".csv";
                csvWriter = new CSVWriter(new java.io.FileWriter(filePath));
                String[] headers = {"ID", "Elapsed(ms)", "Results"};
                csvWriter.writeNext(headers);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        for (Map map : list) {
            BaseResult queryResult;
            Stopwatch watch = Stopwatch.createStarted();
            if (String.valueOf(map.get(TYPE)).equals("sql")) {
                SqlQueryRequestParam sqlQueryRequest = JSON.parseObject(String.valueOf(map.get(TEMPLATE)), SqlQueryRequestParam.class);
                sqlQueryRequest.setExecutionMode(ExecutionMode.ONESHOT.getValue());
                queryResult = queryJobService.createSQLAdHocQuery(sqlQueryRequest);
            } else {
                DSLQueryRequestParam dslQueryRequest = JSON.parseObject(String.valueOf(map.get(TEMPLATE)), DSLQueryRequestParam.class);
                dslQueryRequest.setExecutionMode(ExecutionMode.ONESHOT.getValue());
                queryResult = queryJobService.createDSLAdHocQuery(dslQueryRequest);
            }
            log.info("Benchmark Test, category is: {}, ID is: {}", test, map.get(IDENTIFIER_NAME));
            if (StringUtil.isEmpty(queryResult.getStatistics())) {
                Map<String, Object> statistics = Maps.newLinkedHashMap();
                statistics.put("elapsed", watch.elapsed(TimeUnit.MILLISECONDS));
                queryResult.setStatistics(statistics);
            }
            processQueryResult(benchFileName, infoMap, csvWriter, String.valueOf(map.get(IDENTIFIER_NAME)), queryResult);
        }
        if (csvWriter != null) {
            try {
                csvWriter.close();
            } catch (IOException e) {
                log.error("Failed to close CSVWriter:{}", e.getMessage());
            }
        }

        resultMap.put(benchFileName, buildQueryTimeMetric(infoMap));
        return resultMap;
    }

    private static String getTimeRangeByVariable(List<LinkedHashMap> variables) {
        String startTime = "";
        String endTime = "";
        for (LinkedHashMap variable : variables) {
            if (variable.get("key").equals("start_time")) {
                startTime = String.valueOf(variable.get("default"));
            }
            if (variable.get("key").equals("end_time")) {
                endTime = String.valueOf(variable.get("default"));
            }
        }
        return StrUtil.format("['{}'/'{}']", startTime, endTime);
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

    private void processQueryResult(String name, Map<String, String> infoMap, CSVWriter writer,
                                    String id, BaseResult queryResult) {
        int statusCode = Integer.parseInt(queryResult.getStatus().toString());
        if (statusCode == HttpStatusCodeEnum.SUCCESS.getCode()) {
            if (writer != null) {
                String[] data = {id, String.valueOf(queryResult.getStatistics().get("elapsed")), String.valueOf(queryResult.getData())};
                writer.writeNext(data);
            }
            infoMap.put(id, queryResult.getStatistics().get("elapsed") + "");
        } else if (statusCode == HttpStatusCodeEnum.GATEWAY_TIMEOUT.getCode() || statusCode == HttpStatusCodeEnum.BAD_GATEWAY.getCode()) {
            infoMap.put(id, "status:" + queryResult.getStatus() + ",message:" + queryResult.getMessage());
        } else {
            throw new QGWBusinessException(statusCode, CommonErrorCode.SQL_EXECUTION_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.SQL_EXECUTION_EXCEPTION.getMessage(), name
                            + "," + id + "," + queryResult.getStatus() + "," + queryResult.getMessage()));
        }
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.env = environment;
    }
}
