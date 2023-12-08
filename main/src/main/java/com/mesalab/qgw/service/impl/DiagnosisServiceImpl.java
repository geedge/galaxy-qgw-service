package com.mesalab.qgw.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.io.file.FileReader;
import cn.hutool.core.io.file.FileWriter;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.NumberUtil;
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
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.jayway.jsonpath.JsonPath;
import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;
import com.mesalab.common.httpClient.HttpClientService;
import com.mesalab.common.configuration.HttpConfig;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.*;
import com.mesalab.common.nacos.NacosConfig;
import com.mesalab.qgw.benchmark.DialectWriter;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.qgw.exception.QGWErrorCode;
import com.mesalab.qgw.model.basic.*;
import com.mesalab.qgw.service.QueryService;
import com.mesalab.qgw.service.DiagnosisService;
import com.mesalab.qgw.service.MetadataService;
import com.mesalab.services.common.enums.EntityQueryType;
import com.mesalab.services.common.property.SqlPropertySourceFactory;
import com.mesalab.services.service.EntityService;
import com.geedgenetworks.utils.*;
import com.mesalab.services.service.SQLDatasetService;
import lombok.Data;
import org.apache.avro.Schema;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service("diagnosisService")
@PropertySource(value = "classpath:http-sql-template.sql", factory = SqlPropertySourceFactory.class)
public class
DiagnosisServiceImpl implements DiagnosisService, EnvironmentAware {
    private static final Log log = LogFactory.get();
    private static final String AVRO_SUFFIX = ".avsc";
    private static final String METADATA_PREFIX = "Metadata/";
    private static final String SCHEMA_PREFIX = "Schema/";
    private static final String HBASE_DBNAME2 = "tsg_galaxy";
    private static final String EXECUTE_ENGINE= "execute_engine";
    private static final String IDENTIFIER_NAME = "identifier_name";
    private static final String TEMPLATE = "template";

    @Autowired
    private QueryService queryService;

    @Autowired
    private DialectWriter dialectWriter;
    @Autowired
    private ClickHouseHttpSource clickHouseHttpSource;
    @Autowired
    private DruidIoHttpSource druidIoHttpSource;
    @Autowired
    private HBaseAPISource hBaseAPISource;
    @Autowired
    private MetadataService metadataService;
    @Autowired
    private EntityConfigSource entityConfigSourceTemp;
    @Autowired
    private EntityService entityService;
    @Autowired
    private SQLDatasetService sqlDatasetService;

    @Autowired
    private NacosConfig nacosConfig;
    @Autowired
    private ConfigService systemConfigService;

    @Autowired
    private HttpClientService httpClientService;
    @Autowired
    HttpConfig httpConfig;

    @PostConstruct
    public void init() {
        entityConfigSource = this.entityConfigSourceTemp;
    }

    private Environment env;
    private static EntityConfigSource entityConfigSource;
    private final static String DATA_ID = "version.json";

    private static Pattern pTTL = Pattern.compile(".*toIntervalSecond\\((\\d+)\\)", Pattern.CASE_INSENSITIVE);
    private static final String trafficPort = "8123";


    @Override
    public JSONObject getVersionInfo() {
        JSONObject version = null;
        try {
            String content = systemConfigService.getConfig(DATA_ID, nacosConfig.getGroup(), 3000);
            version = JSONObject.parseObject(content);
        } catch (NacosException e) {
            e.printStackTrace();
        }
        return version;
    }


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
            List<Object> data = Lists.newArrayList();
            Map<String, Map<String, String>> clickhouseResult = parseSchemaByDatasource(clickHouseHttpSource.getDbName(), metadataService.getAllTable());
            Map<String, Map<String, String>> druidResult = parseSchemaByDatasource(druidIoHttpSource.getDbname(), metadataService.getAllTable());
            Map<String, Map<String, String>> hbaseResult = parseSchemaByDatasource(hBaseAPISource.getDbName(), metadataService.getAllTable());
            Map<String, Map<String, String>> hbase2Result = parseSchemaByDatasource(HBASE_DBNAME2, metadataService.getAllTable());

            data.add(clickhouseResult.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
            data.add(druidResult.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
            data.add(hbaseResult.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
            data.add(hbase2Result.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));

            long executeTime = watch.elapsed(TimeUnit.MILLISECONDS);
            statistics.put("elapsed", executeTime);
            result = BaseResultGenerator.success("Avro Schema Validation Success.", data, statistics);
            result.setFormatType(OutputModeEnum.JSON.getValue());
        } catch (RuntimeException e) {
            log.error("Avro Schema Validation Fail:{}", e);
            throw new QGWBusinessException(ResultStatusEnum.SERVER_ERROR.getCode(),
                    QGWErrorCode.SQL_QUERY_FEDERATION_EXCEPTION.getCode(),
                    String.format(QGWErrorCode.SQL_QUERY_FEDERATION_EXCEPTION.getMessage(),e.getMessage()));

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
            List<Object> data = Lists.newArrayList();
            checkCKIndexKey(clickHouseHttpSource.getDbName(), metadataService.getAllTable());
            Map<String, Map<String, String>> clickhouseResult = checkMetadataByDatasource(clickHouseHttpSource.getDbName(), metadataService.getAllTable());
            Map<String, Map<String, String>> druidResult = checkMetadataByDatasource(druidIoHttpSource.getDbname(), metadataService.getAllTable());
            Map<String, Map<String, String>> hbaseResult = checkMetadataByDatasource(hBaseAPISource.getDbName(), metadataService.getAllTable());
            Map<String, Map<String, String>> hbase2Result = checkMetadataByDatasource(HBASE_DBNAME2, metadataService.getAllTable());

            data.add(clickhouseResult.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
            data.add(druidResult.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
            data.add(hbaseResult.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
            data.add(hbase2Result.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));

            statistics.put("elapsed", watch.elapsed(TimeUnit.MILLISECONDS));
            result = BaseResultGenerator.success("Metadata Validation Success, Only Table Exist Fields detail.", data, statistics);
            result.setFormatType(OutputModeEnum.JSON.getValue());
        } catch (RuntimeException e) {
            log.error("Metadata Validation Fail: {}", e.getMessage());
            throw new QGWBusinessException(ResultStatusEnum.SERVER_ERROR.getCode(),
                    ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(),
                    String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(), e.getMessage()));
        }
        return result;
    }


    @Override
    public BaseResult runPocSQL(boolean isSaved, String option, String category) {
        BaseResult result = null;
        Map<String, Object> statistics = Maps.newLinkedHashMap();
        Map<String, Map<String, String>> mergeResult;
        Map<String, Map<String, String>> engineResult;
        Map<String, Map<String, String>> clickhouseResult;
        Map<String, Map<String, String>> druidResult;
        Map<String, Map<String, String>> hbaseResult;
        try {
            Map<String, Object> datasetResult = generateDatasets(category);
            List<Map<String, String>> dataSetlist = (ArrayList<Map<String, String>>) datasetResult.get("list");
            List<String> druidIdList = Lists.newLinkedList();
            List<String> ckIdList = Lists.newLinkedList();
            List<String> engineIdList = Lists.newLinkedList();
            List<String> hbaseIdList = Lists.newLinkedList();

            List<String> druidSqlList = Lists.newLinkedList();
            List<String> ckSqlList = Lists.newLinkedList();
            List<String> engineSqlList = Lists.newLinkedList();
            List<String> hbaseSqlList = Lists.newLinkedList();
            Stopwatch watch = Stopwatch.createStarted();
            if (CollectionUtil.isNotEmpty(dataSetlist)){
                for (int i = 0; i < dataSetlist.size(); i++) {
                    if (dataSetlist.get(i).get(EXECUTE_ENGINE).equalsIgnoreCase(DBTypeEnum.DRUID.getValue())) {
                        druidIdList.add(dataSetlist.get(i).get(IDENTIFIER_NAME));
                        druidSqlList.add(dataSetlist.get(i).get(TEMPLATE));
                    } else if (dataSetlist.get(i).get(EXECUTE_ENGINE).equalsIgnoreCase(DBTypeEnum.CLICKHOUSE.getValue())) {
                        ckIdList.add(dataSetlist.get(i).get(IDENTIFIER_NAME));
                        ckSqlList.add(dataSetlist.get(i).get(TEMPLATE));
                    } else if (dataSetlist.get(i).get(EXECUTE_ENGINE).equalsIgnoreCase(DBTypeEnum.HBASE.getValue())) {
                        hbaseIdList.add(dataSetlist.get(i).get(IDENTIFIER_NAME));
                        hbaseSqlList.add(dataSetlist.get(i).get(TEMPLATE));
                    } else {
                        engineIdList.add(dataSetlist.get(i).get(IDENTIFIER_NAME));
                        engineSqlList.add(dataSetlist.get(i).get(TEMPLATE));
                    }
                }
            }

            Optional.of(new File(dialectWriter.buildPocSQL(druidSqlList, DBTypeEnum.DRUID.getValue(), category)));
            Optional.of(new File(dialectWriter.buildPocSQL(ckSqlList, DBTypeEnum.CLICKHOUSE.getValue(), category)));
            Optional.of(new File(dialectWriter.buildPocSQL(hbaseSqlList, DBTypeEnum.HBASE.getValue(), category)));
            Optional.of(new File(dialectWriter.buildPocSQL(engineSqlList, DBTypeEnum.ENGINE.getValue(), category)));

            clickhouseResult = buildResult(DBTypeEnum.CLICKHOUSE.getValue(), ckIdList, isSaved, option);
            druidResult = buildResult(DBTypeEnum.DRUID.getValue(), druidIdList, isSaved, option);
            engineResult = buildResult(DBTypeEnum.ENGINE.getValue(), engineIdList, isSaved, option);
            hbaseResult = buildResult(DBTypeEnum.HBASE.getValue(), hbaseIdList, isSaved, option);

            mergeResult = Stream
                    .concat(clickhouseResult.entrySet().stream(), druidResult.entrySet().stream())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            mergeResult = Stream.concat(mergeResult.entrySet().stream(), engineResult.entrySet().stream())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            mergeResult = Stream.concat(mergeResult.entrySet().stream(), hbaseResult.entrySet().stream())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            statistics.put("elapsed", watch.elapsed(TimeUnit.MILLISECONDS));
            result = BaseResultGenerator.success("SQL Queries Success.", mergeResult, statistics);
            result.setFormatType(OutputModeEnum.JSON.getValue());
        } catch (RuntimeException e) {
            log.error("Execute Poc SQL Fail:{}", e);
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(),
                    QGWErrorCode.SQL_EXECUTION_SERVER_EXCEPTION.getCode(),
                    String.format(QGWErrorCode.SQL_EXECUTION_SERVER_EXCEPTION.getMessage(),e.getMessage()));
        }
        return result;
    }

    @Override
    public BaseResult getPocSQL(String dialect, Integer queryNo) {
        BaseResult result;
        Optional<File> testFile;
        Map<String, Object> statistics = Maps.newLinkedHashMap();
        Map<String, String> dialectResult = Maps.newHashMap();
        try{
            Stopwatch watch = Stopwatch.createStarted();
            String path = new File("").getCanonicalPath()
                    + File.separator + "benchmark" + File.separator + dialect + "_queries" + "_"
                    + DateUtils.getCurrentDate(DateUtils.YYYYMMDD) + ".sql";
            if (Files.exists(Paths.get(path))) {
                dialectResult = getPocSqlByDatasource(Optional.of(new File(path)), queryNo);
            } else {
                testFile = Optional.of(new File(dialectWriter.buildPocSQL(Lists.newLinkedList(), dialect, null)));
                if (testFile.isPresent()) {
                    if (!testFile.get().isFile()) {
                        log.warn("UnKnow File " + testFile.get());
                        return null;
                    }
                    dialectResult = getPocSqlByDatasource(testFile, queryNo);
                }
            }
            statistics.put("elapsed", watch.elapsed(TimeUnit.MILLISECONDS));
            result = BaseResultGenerator.success("Get SQL Success.", dialectResult, statistics);
            result.setFormatType(OutputModeEnum.JSON.getValue());
        } catch (RuntimeException | IOException e) {
            log.error("Get Poc SQL Fail:{}", e);
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(),
                    ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),e.getMessage()));
        }
        return result;
    }

    @Override
    public BaseResult consistencyCheck() {
        List<Map<String, Object>> dataList = Lists.newArrayList();
        Map<String, Object> logDiff = Maps.newHashMap();
        Map tables = metadataService.getSchemaInfo(MetadataTypeEnum.TABLES.getValue(), clickHouseHttpSource.getDbName(), false);
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
                    Map schemaMap = metadataService.getSchemaInfo("fields", tableName, false);
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
            throw new QGWBusinessException(ResultStatusEnum.SERVER_ERROR.getCode(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(),
                    String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(),ex.getMessage()));
        }
        if (changeTTL.isEmpty()) {
            return BaseResultGenerator.success("ok", dataList);
        }
        dataList.add(logDiff);
        return BaseResultGenerator.success("Log streaming setting task not executed or failed", dataList);
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
        if (Integer.parseInt(result.get("status")) != ResultStatusEnum.SUCCESS.getCode()) {
            log.error(" query table TTL error: {}", result.toString());
            throw new QGWBusinessException(ResultStatusEnum.SERVER_ERROR.getCode(), QGWErrorCode.SQL_EXECUTION_SERVER_EXCEPTION.getCode(),
                    String.format(QGWErrorCode.SQL_EXECUTION_SERVER_EXCEPTION.getMessage(),result.toString()));
        }
        Object tableTTLInDB = null;
        Map<String, Object> tableDiff = Maps.newHashMap();
        Map o = (Map) JSON.parseObject(result.get("result"), Map.class);
        Map<String, Object> resultMap = (Map<String, Object>) o;
        for (Map<String, Object> datum : (List<Map<String, Object>>)resultMap.get("data")) {
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

    private List<Map<String, Object>> getFieldDiff(String ipPort, String tableName, Map<String, Object> schemaMap) {
        List<Map<String, Object>> fieldDiff = Lists.newArrayList();
        Map<String, String> result = executeHttpGetOfCK(ipPort, "describe " + tableName + "_local", clickHouseHttpSource.getDbName());
        if (Integer.parseInt(result.get("status")) != ResultStatusEnum.SUCCESS.getCode()) {
            log.error(" query table field TTL error: {}", result.toString());
            throw new QGWBusinessException(ResultStatusEnum.SERVER_ERROR.getCode(), QGWErrorCode.SQL_EXECUTION_SERVER_EXCEPTION.getCode(),
                    String.format(QGWErrorCode.SQL_EXECUTION_SERVER_EXCEPTION.getMessage(),result.toString()));
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
        BaseResult baseResult = queryService.executeQuery(QueryProfile.builder().query("SELECT DISTINCT concat(host_address,':','" + trafficPort + "') as endpoint FROM clusters where cluster = 'ck_cluster'").build());
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
    /**
     *
     * @param param
     * @Description 获取Entity推荐全网占比统计信息
     * @author wanghao
     * @date 2022/7/7 15:49
     */
    @Override
    public BaseResult getMessageInfo(String param) {
        BaseResult baseResult = null;
        Map<String, String> data = Maps.newHashMap();
        Map<String, Object> statistics = Maps.newLinkedHashMap();
        Stopwatch watch = Stopwatch.createStarted();
        String timeInterval = getTimeInterval(-24 * 60);
        String datasource = "session_record";
        if (EntityQueryType.TOPSERVERIP.getType().equalsIgnoreCase(param)) {
            String message = getTopServerIpMessage(timeInterval, datasource);
            data.put("message", message);
        } else if (EntityQueryType.TOPSNI.getType().equalsIgnoreCase(param)) {
            String message = getTopSniMessage(timeInterval, datasource);
            data.put("message", message);
        }
        statistics.put("elapsed", watch.elapsed(TimeUnit.MILLISECONDS));
        baseResult = BaseResultGenerator.success("ok", data, statistics);
        return baseResult;
    }

    private String getTopServerIpMessage(String timeInterval, String datasource) {
        String tcpSessionSql = String.format(Objects.requireNonNull(env.getProperty("ENTITY_TCP_SESSION")), datasource, timeInterval,
                entityConfigSource.getTopServerIPByTCPSession());
        List<Map> tcpSessionResult = getData(queryService.executeQuery(queryBuild(tcpSessionSql)));

        String tcpClientIpsSql = String.format(Objects.requireNonNull(env.getProperty("TOP_ENTITY_TCP_UNIQ_CLIENT_IPS")), datasource, timeInterval,
                datasource, timeInterval, entityConfigSource.getTopServerIPByTCPUniqClientIP());
        List<Map> tcpClientIpsResult = getData(queryService.executeQuery(queryBuild(tcpClientIpsSql)));

        String udpSessionSql = String.format(Objects.requireNonNull(env.getProperty("ENTITY_UDP_SESSION")), datasource, timeInterval,
                entityConfigSource.getTopServerIPByUDPBySession());
        List<Map> udpSessionResult = getData(queryService.executeQuery(queryBuild(udpSessionSql)));

        String udpClientIpsSql = String.format(Objects.requireNonNull(env.getProperty("TOP_ENTITY_UDP_UNIQ_CLIENT_IPS")), datasource, timeInterval,
                datasource, timeInterval, entityConfigSource.getTopServerIPByUDPUniqClientIP());
        List<Map> udpClientIpsResult = getData(queryService.executeQuery(queryBuild(udpClientIpsSql)));

        String tcpTotalSessionSql = String.format(Objects.requireNonNull(env.getProperty("TOTAL_ENTITY_TCP_SESSION")), datasource, timeInterval);
        List<Map> tcpTotalSessionResult = getData(queryService.executeQuery(queryBuild(tcpTotalSessionSql)));

        String tcpTotalClientIpsSql = String.format(Objects.requireNonNull(env.getProperty("TOTAL_ENTITY_TCP_UNIQ_CLIENT_IPS")), datasource, timeInterval);
        List<Map> tcpTotalClientIpResult = getData(queryService.executeQuery(queryBuild(tcpTotalClientIpsSql)));

        String udpTotalSessionSql = String.format(Objects.requireNonNull(env.getProperty("TOTAL_ENTITY_UDP_SESSION")), datasource, timeInterval);
        List<Map> udpTotalSessionResult = getData(queryService.executeQuery(queryBuild(udpTotalSessionSql)));

        String udpTotalClientIpsSql = String.format(Objects.requireNonNull(env.getProperty("TOTAL_ENTITY_UDP_UNIQ_CLIENT_IPS")), datasource, timeInterval);
        List<Map> udpTotalClientIpsResult = getData(queryService.executeQuery(queryBuild(udpTotalClientIpsSql)));

        long tcpSessions = 0;
        long tcpClientIps = 0;
        long udpSessions = 0;
        long udpClientIps = 0;
        long tcpTotalSessions = StringUtil.isEmpty(tcpTotalSessionResult.get(0).get("sessions")) ? 0 : Long.parseLong(tcpTotalSessionResult.get(0).get("sessions").toString());
        long tcpTotalClientIps = StringUtil.isEmpty(tcpTotalClientIpResult.get(0).get("uniq_client_ips")) ? 0 : Long.parseLong(tcpTotalClientIpResult.get(0).get("uniq_client_ips").toString());
        long udpTotalSessions = StringUtil.isEmpty(udpTotalSessionResult.get(0).get("sessions")) ? 0 : Long.parseLong(udpTotalSessionResult.get(0).get("sessions").toString());
        long udpTotalClientIps = StringUtil.isEmpty(udpTotalClientIpsResult.get(0).get("uniq_client_ips")) ? 0 : Long.parseLong(udpTotalClientIpsResult.get(0).get("uniq_client_ips").toString());
        for (Map item : tcpSessionResult) {
            tcpSessions += StringUtil.isEmpty(item.get("sessions")) ? 0 : Long.parseLong(item.get("sessions").toString());
        }
        for (Map item : tcpClientIpsResult) {
            tcpClientIps += StringUtil.isEmpty(item.get("client_ips")) ? 0 : Long.parseLong(item.get("client_ips").toString());
        }
        for (Map item : udpSessionResult) {
            udpSessions += StringUtil.isEmpty(item.get("sessions")) ? 0 : Long.parseLong(item.get("sessions").toString());
        }
        for (Map item : udpClientIpsResult) {
            udpClientIps += StringUtil.isEmpty(item.get("client_ips")) ? 0 : Long.parseLong(item.get("client_ips").toString());
        }

        String message = StrUtil.format("Top {} Server IPs, {} of TCP sessions; " +
                        "Top {} Server IPs, {} of TCP uniq client ips; " +
                        "Top {} Server IPs, {} of UDP(53,443) sessions; " +
                        "Top {} Server IPs, {} of UDP uniq client ips",
                entityConfigSource.getTopServerIPByTCPSession(), tcpTotalSessions == 0 ? "-" : NumberUtil.decimalFormat("#.##%", (tcpSessions * 1.0) / tcpTotalSessions),
                entityConfigSource.getTopServerIPByTCPUniqClientIP(), tcpTotalClientIps == 0 ? "-" : NumberUtil.decimalFormat("#.##%", (tcpClientIps * 1.0) / tcpTotalClientIps),
                entityConfigSource.getTopServerIPByUDPBySession(), udpTotalSessions == 0 ? "-" : NumberUtil.decimalFormat("#.##%", (udpSessions * 1.0) / udpTotalSessions),
                entityConfigSource.getTopServerIPByUDPUniqClientIP(), udpTotalClientIps == 0 ? "-" : NumberUtil.decimalFormat("#.##%", (udpClientIps * 1.0) / udpTotalClientIps));
        return message;
    }

    private String getTopSniMessage(String timeInterval, String datasource) {
        String topSniSql = String.format(Objects.requireNonNull(env.getProperty("ENTITY_TOP_SNI")), datasource, timeInterval,
                entityConfigSource.getTopSNIDefaultSize());
        List<Map> topSniResult = getData(queryService.executeQuery(queryBuild(topSniSql)));

        String totalSniSql = String.format(Objects.requireNonNull(env.getProperty("ENTITY_SNI_TOTAL")), datasource, timeInterval);
        List<Map> totalSniResult = getData(queryService.executeQuery(queryBuild(totalSniSql)));

        int cdnCount = 0;
        long topSniBytes = 0;
        long topSniSessions = 0;
        long totalSniBytes = StringUtil.isEmpty(totalSniResult.get(0).get("bytes")) ? 0 : Long.parseLong(totalSniResult.get(0).get("bytes").toString());
        long totalSniSessions = StringUtil.isEmpty(totalSniResult.get(0).get("sessions")) ? 0 : Long.parseLong(totalSniResult.get(0).get("sessions").toString());
        Map<String, List<String>> cdnMap = entityService.buildDomainHashTable(getCfgCDN());
        for (Map item : topSniResult) {
            if (entityService.inCDN(cdnMap, String.valueOf(item.get("ssl_sni")))) {
                cdnCount++;
            }
            topSniBytes += StringUtil.isEmpty(item.get("bytes")) ? 0 : Long.parseLong(item.get("bytes").toString());
            topSniSessions += StringUtil.isEmpty(item.get("sessions")) ? 0 : Long.parseLong(item.get("sessions").toString());
        }

        String message = StrUtil.format("Top {} SNI, {} of ssl bytes, {} of ssl sessions, {} popular CDNs",
                entityConfigSource.getTopSNIDefaultSize(),
                totalSniBytes == 0 ? "-" : NumberUtil.decimalFormat("#.##%", (topSniBytes * 1.0) / totalSniBytes),
                totalSniSessions == 0 ? "-" : NumberUtil.decimalFormat("#.##%", (topSniSessions * 1.0) / totalSniSessions),
                cdnCount == 0 ? "-" : cdnCount);

        return message;
    }


    private String getTimeInterval(int defaultInterval) {
        Date now = new Date();
        Date someMinute = DateUtils.getSomeMinute(now, defaultInterval);
        String end = DateUtils.getFormatDate(now, DateUtils.YYYY_MM_DD_HH24_MM_SS);
        String start = DateUtils.getFormatDate(someMinute, DateUtils.YYYY_MM_DD_HH24_MM_SS);
        List<String> list = Lists.newArrayList(start.concat("/").concat(end));
        StringBuilder timeInterval = new StringBuilder();
        String[] intervals = getIntervals(list);
        timeInterval.append("recv_time >= UNIX_TIMESTAMP('").append(intervals[0]).append("') AND recv_time< UNIX_TIMESTAMP('").append(intervals[1]).append("')");
        return timeInterval.toString();
    }

    private String[] getIntervals(List<String> intervals) {
        return intervals.get(0).split("/");
    }

    public List<Map> getData(BaseResult baseResult) {
        List<Map> dataList = (List<Map>) baseResult.getData();
        return dataList;
    }

    private Map<String, List<String>> getCfgCDN() {
        Object codeInfo = metadataService.getCfg("public_code_info.json");
        if (StringUtil.isNotEmpty(codeInfo)) {
            Map<String, Map<String, List<String>>> data = JSON.parseObject(codeInfo.toString(), Map.class);
            return Maps.newHashMap(data.get("CDN"));
        }
        return Maps.newHashMap();
    }

    private Map<String, Object> generateDatasets(String category) {
        List<String> categorylist = Lists.newLinkedList();
        List<Record> list = Db.find(String.format(Objects.requireNonNull(env.getProperty("SQL_DATASETS_CATEGORY"))));
        list.forEach(record -> categorylist.add(String.valueOf(record.getColumns().get("category"))));
        if (CollectionUtil.isNotEmpty(categorylist) && StrUtil.isNotBlank(category) && !categorylist.contains(category)) {
            throw new QGWBusinessException(HttpStatus.HTTP_BAD_REQUEST, ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), QGWMessageConst.DIAGNOSIS_CATEGORY_ERROR));
        }
        return sqlDatasetService.getDatasets(Lists.newArrayList(), category, null);
    }

    private Map<String, Map<String,String>> buildResult(String executeEngine,List<String> list,boolean isSaved, String option) {
        Map<String, Map<String, String>> resultMap = Maps.newLinkedHashMap();
        Map<String, String> infoMap = Maps.newLinkedHashMap();
        FileWriter writer = null;
        String benchFileName = executeEngine + "_queries_" + DateUtils.getCurrentDate(DateUtils.YYYYMMDD) + ".sql";
        if (isSaved && DiagnosisOptionEnum.EXECUTION.getValue().equalsIgnoreCase(option)) {
            File file = new File("benchmark" + File.separator + benchFileName + "." + IdUtil.simpleUUID() + ".dat");
            writer = FileWriter.create(file);
        }
        int index = 0;
        for (String line : list) {
            index++;
            Stopwatch watch = Stopwatch.createStarted();
            BaseResult queryResult = sqlDatasetService.getPreview(line, option);
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
            infoMap.put("Min", String.valueOf(CommonUtil.round(StatUtils.min(values),2)));
            infoMap.put("Mean", String.valueOf(CommonUtil.round(StatUtils.mean(values),2)));
            infoMap.put("Median", String.valueOf(CommonUtil.round(StatUtils.percentile(values, 50),2)));
            infoMap.put("P95", String.valueOf(CommonUtil.round(StatUtils.percentile(values, 95),2)));
            infoMap.put("P99", String.valueOf(CommonUtil.round(StatUtils.percentile(values, 99),2)));
            infoMap.put("MAX", String.valueOf(CommonUtil.round(StatUtils.max(values),2)));
        }
        return infoMap;
    }

    private void processQueryResult(String name, Map<String, String> infoMap, FileWriter writer, int index, BaseResult queryResult) {
        int statusCode = Integer.parseInt(queryResult.getStatus().toString());
        if (statusCode == ResultStatusEnum.SUCCESS.getCode()) {
            if (writer != null) {
                writer.append("--Query" + index + "\t" + "Elapsed(ms):" + queryResult.getStatistics().get("elapsed") + "\n");
                writer.append(queryResult.getMeta() + "\n");
                writer.append(queryResult.getData() + "\n");
            }
            infoMap.put("Query" + index, queryResult.getStatistics().get("elapsed") + "");
        } else if (statusCode == ResultStatusEnum.GATEWAY_TIMEOUT.getCode() || statusCode == ResultStatusEnum.BAD_GATEWAY.getCode()) {
            infoMap.put("Query" + index, "status:" + queryResult.getStatus() + ",message:" + queryResult.getMessage());

        } else {
            throw new QGWBusinessException(statusCode, QGWErrorCode.SQL_EXECUTION_SERVER_EXCEPTION.getCode(),
                    String.format(QGWErrorCode.SQL_EXECUTION_SERVER_EXCEPTION.getMessage(),name
                            + ",Query" + index + "," + queryResult.getStatus() + "," + queryResult.getMessage()));
        }
    }

    private QueryProfile queryBuild(String sql) {
        return QueryProfile.builder().format(OutputModeEnum.JSON.getValue()).query(sql).build();
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
            BaseResult baseResult = getBaseResultByEngine(QueryProfile.builder().option(QueryOptionEnum.REAL_TIME.getValue()).query("describe " + tableName).build());
            if (ResultStatusEnum.SUCCESS.getCode() != baseResult.getStatus()) {
                log.error("The Table[" + tableName + "] Structure query failed" + baseResult.getMessage());
                throw new QGWBusinessException(ResultStatusEnum.SERVER_ERROR.getCode(),
                        QGWErrorCode.SQL_EXECUTION_SERVER_EXCEPTION.getCode(),
                        String.format(QGWErrorCode.SQL_EXECUTION_SERVER_EXCEPTION.getMessage()," The Table[" + tableName + "] Structure query failed"));
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
                throw new QGWBusinessException(ResultStatusEnum.SERVER_ERROR.getCode(),
                        QGWErrorCode.SQL_QUERY_FEDERATION_EXCEPTION.getCode(),
                        String.format(QGWErrorCode.SQL_QUERY_FEDERATION_EXCEPTION.getMessage(),
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

    private void checkCKIndexKey(String dbName, List<String> tables) {
        for (String tableName : tables) {
            Schema schema = metadataService.getSchemaByName(tableName);
            if (!dbName.equals(schema.getNamespace())) continue;
            BaseResult indexKeyResult = getBaseResultByEngine(QueryProfile.builder().option(QueryOptionEnum.REAL_TIME.getValue()).query(String.format(Objects.requireNonNull(env.getProperty("TABLE_INDEX_KEY")), tableName, clickHouseHttpSource.getDbName())).build());
            if (ResultStatusEnum.SUCCESS.getCode() != indexKeyResult.getStatus()) {
                log.error("The Table[" + tableName + "] index key query failed" + indexKeyResult.getMessage());
                throw new QGWBusinessException(ResultStatusEnum.SERVER_ERROR.getCode(),
                        QGWErrorCode.SQL_EXECUTION_SERVER_EXCEPTION.getCode(),
                        String.format(QGWErrorCode.SQL_EXECUTION_SERVER_EXCEPTION.getMessage(), " The Table[" + tableName + "] index key query failed"));
            }
            List<Map<String, Object>> dataList = (List<Map<String, Object>>) indexKeyResult.getData();
            List<String> dbIndexKey = StringUtil.isEmpty(dataList.get(0)) ? Lists.newArrayList() : (List<String>) dataList.get(0).get("index_key");
            List<String> schemaIndexKey = metadataService.getIndexKey(tableName);
            if (schemaIndexKey.equals(dbIndexKey)) {
                continue;
            }
            log.error("{} schema index key inconsistent with DB, schema: {}, db: {}", tableName, schemaIndexKey, dbIndexKey);
            throw new QGWBusinessException(ResultStatusEnum.SERVER_ERROR.getCode(),
                    QGWErrorCode.SCHEMA_WITCH_DB_INCONSISTENTY.getCode(),
                    String.format(QGWErrorCode.SCHEMA_WITCH_DB_INCONSISTENTY.getMessage(),
                            String.format("%s schema index key is: %s, db index key is: %s", tableName, schemaIndexKey, dbIndexKey)));
        }
    }
    private BaseResult getBaseResultByEngine(QueryProfile queryProfile) {
        return queryService.executeQuery(queryProfile);
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
            Map schemaMap = JSON.parseObject(schema.toString(), Map.class);
            if (!dbName.equals(schema.getNamespace())) continue;
            List<Map<String, Object>> fields = (List<Map<String, Object>>) schemaMap.get("fields");
            infoMap.put(tableName, fields.size() + " Fields");
        }
        resultMap.put(SCHEMA_PREFIX + dbName, infoMap);

        return resultMap;
    }


    @Override
    public void setEnvironment(Environment environment) {
        this.env = environment;
    }

    @Data
    class DruidQueryParam {
        private String query;
        private Map<String, String> context = Maps.newHashMap();
        private String resultFormat;
    }


}
