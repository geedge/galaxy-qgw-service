package com.mesalab.qgw.service.impl;

import cn.hutool.core.io.file.FileReader;
import cn.hutool.core.io.file.FileWriter;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.jayway.jsonpath.JsonPath;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.*;
import com.mesalab.common.utils.sqlparser.SQLVisitorUtil;
import com.mesalab.qgw.benchmark.elasticsearch.ElasticSearchWriter;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.qgw.exception.QGWErrorCode;
import com.mesalab.qgw.model.api.*;
import com.mesalab.qgw.benchmark.clickhouse.ClickhouseWriter;
import com.mesalab.qgw.benchmark.druid.DruidWriter;
import com.mesalab.qgw.benchmark.engine.EngineWriter;
import com.mesalab.qgw.benchmark.hbase.HbaseWriter;
import com.mesalab.qgw.dialect.HbaseDialect;
import com.mesalab.qgw.service.ApiService;
import com.mesalab.qgw.service.DiagnosisService;
import com.mesalab.qgw.service.MetadataService;
import com.mesalab.services.common.enums.EntityQueryType;
import com.mesalab.services.common.property.SqlPropertySourceFactory;
import com.mesalab.services.service.EntityService;
import com.zdjizhi.utils.*;
import lombok.Data;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.Select;
import org.apache.avro.Schema;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.TimeUnit;
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
    private static final String DRUID_PREFIX = "druid_";
    private static final String CK_PREFIX = "ck_";
    private static final String ENGINE_PREFIX = "engine_";
    private static final String HBASE_PREFIX = "hbase_";
    private static final String ES_PREFIX = "es_";
    private static final String HBASE_DBNAME2 = "tsg_galaxy";

    @Autowired
    private HttpClientService httpClientService;
    @Autowired
    private HttpConfig httpConfig;

    @Autowired
    private ApiService apiService;

    @Autowired
    private ClickhouseWriter clickhouseWriter;
    @Autowired
    private EngineWriter engineWriter;
    @Autowired
    private DruidWriter druidWriter;
    @Autowired
    private HbaseWriter hbaseWriter;
    @Autowired
    private ElasticSearchWriter elasticSearchWriter;
    @Autowired
    private ClickHouseHttpSource clickHouseHttpSource;
    @Autowired
    private DruidIoHttpSource druidIoHttpSource;
    @Autowired
    private HBaseAPISource hBaseAPISource;
    @Autowired
    private RestClient restClient;
    @Autowired
    private ElasticsearchHttpSource elasticsearchHttpSource;
    @Autowired
    private MetadataService metadataService;
    @Autowired
    private EntityConfigSource entityConfigSourceTemp;
    @Autowired
    private EntityService entityService;

    @PostConstruct
    public void init() {
        entityConfigSource = this.entityConfigSourceTemp;
    }

    private Environment env;
    private static EntityConfigSource entityConfigSource;

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
            Map<String, Map<String, String>> elasticsearchResult = parseSchemaByDatasource(elasticsearchHttpSource.getDbname(), metadataService.getAllTable());

            data.add(clickhouseResult.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
            data.add(druidResult.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
            data.add(hbaseResult.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
            data.add(hbase2Result.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
            data.add(elasticsearchResult.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));

            long executeTime = watch.elapsed(TimeUnit.MILLISECONDS);
            statistics.put("elapsed", executeTime);
            result = BaseResultGenerator.success("Avro Schema Validation Success.", data, statistics);
            result.setFormatType(QueryFormatEnum.JSON.getValue());
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
            Map<String, Map<String, String>> clickhouseResult = checkMetadataByDatasource(clickHouseHttpSource.getDbName(), metadataService.getAllTable());
            checkCkIndexKey(clickHouseHttpSource.getDbName(), metadataService.getAllTable());
            Map<String, Map<String, String>> druidResult = checkMetadataByDatasource(druidIoHttpSource.getDbname(), metadataService.getAllTable());
            Map<String, Map<String, String>> hbaseResult = checkMetadataByDatasource(hBaseAPISource.getDbName(), metadataService.getAllTable());
            Map<String, Map<String, String>> hbase2Result = checkMetadataByDatasource(HBASE_DBNAME2, metadataService.getAllTable());
            Map<String, Map<String, String>> elasticsearchResult = checkMetadataByDatasource(elasticsearchHttpSource.getDbname(), metadataService.getAllTable());

            data.add(clickhouseResult.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
            data.add(druidResult.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
            data.add(hbaseResult.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
            data.add(hbase2Result.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
            data.add(elasticsearchResult.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));

            statistics.put("elapsed", watch.elapsed(TimeUnit.MILLISECONDS));
            result = BaseResultGenerator.success("Metadata Validation Success, Only Table Exist Fields detail.", data, statistics);
            result.setFormatType(QueryFormatEnum.JSON.getValue());
        } catch (RuntimeException e) {
            log.error("Metadata Validation Fail:{}", e);
            throw new QGWBusinessException(ResultStatusEnum.SERVER_ERROR.getCode(),
                    ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),e.getMessage()));
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
        Map<String, Map<String, String>> elasticsearchResult;
        try {
            Stopwatch watch = Stopwatch.createStarted();
            Optional<File> druidTestFile = Optional.of(new File(druidWriter.buildPocSQL()));
            Optional<File> ckTestFile = Optional.of(new File(clickhouseWriter.buildPocSQL()));
            Optional<File> hbaseTestFile = Optional.of(new File(hbaseWriter.buildPocSQL()));
            Optional<File> engineTestFile = Optional.of(new File(engineWriter.buildPocSQL()));
            Optional<File> esTestFile = Optional.of(new File(elasticSearchWriter.buildPocSQL()));

            if (DiagnosisOptionEnum.PARSE.getValue().equalsIgnoreCase(option)) {
                druidResult = executeSyntaxCheckByDatasource(druidTestFile, QueryOptionEnum.SYNTAX_PARSE.getValue());
                clickhouseResult = executeSyntaxCheckByDatasource(ckTestFile, QueryOptionEnum.SYNTAX_PARSE.getValue());
                hbaseResult = executeSyntaxCheckByDatasource(hbaseTestFile, QueryOptionEnum.SYNTAX_PARSE.getValue());
                engineResult = executeSyntaxCheckByDatasource(engineTestFile, QueryOptionEnum.SYNTAX_PARSE.getValue());
                elasticsearchResult = executeSyntaxCheckByDatasource(esTestFile, QueryOptionEnum.SYNTAX_PARSE.getValue());
            } else if (DiagnosisOptionEnum.VALIDATION.getValue().equalsIgnoreCase(option)) {
                druidResult = executeSyntaxCheckByDatasource(druidTestFile, QueryOptionEnum.SYNTAX_VALIDATION.getValue());
                clickhouseResult = executeSyntaxCheckByDatasource(ckTestFile, QueryOptionEnum.SYNTAX_VALIDATION.getValue());
                hbaseResult = executeSyntaxCheckByDatasource(hbaseTestFile, QueryOptionEnum.SYNTAX_VALIDATION.getValue());
                engineResult = executeSyntaxCheckByDatasource(engineTestFile, QueryOptionEnum.SYNTAX_VALIDATION.getValue());
                elasticsearchResult = executeSyntaxCheckByDatasource(esTestFile, QueryOptionEnum.SYNTAX_VALIDATION.getValue());
            } else if (DiagnosisOptionEnum.EXECUTION.getValue().equalsIgnoreCase(option)) {
                druidResult = executePocSqlByDatasource(druidTestFile, isSaved, QueryOptionEnum.REAL_TIME.getValue());
                clickhouseResult = executePocSqlByDatasource(ckTestFile, isSaved, QueryOptionEnum.REAL_TIME.getValue());
                hbaseResult = executePocSqlByDatasource(hbaseTestFile, isSaved, QueryOptionEnum.REAL_TIME.getValue());
                engineResult = executePocSqlByDatasource(engineTestFile, isSaved, QueryOptionEnum.REAL_TIME.getValue());
                elasticsearchResult = executePocSqlByDatasource(esTestFile, isSaved, QueryOptionEnum.REAL_TIME.getValue());
            } else {
                throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(),
                        ResultCodeEnum.PARAMETER_ERROR.getCode(),
                        String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), QGWMessageConst.OPTION_IS_NOT_SUPPORTED));
            }

            mergeResult = Stream
                    .concat(clickhouseResult.entrySet().stream(), druidResult.entrySet().stream())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            mergeResult = Stream.concat(mergeResult.entrySet().stream(), engineResult.entrySet().stream())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            mergeResult = Stream.concat(mergeResult.entrySet().stream(), hbaseResult.entrySet().stream())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            mergeResult = Stream.concat(mergeResult.entrySet().stream(), elasticsearchResult.entrySet().stream())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            statistics.put("elapsed", watch.elapsed(TimeUnit.MILLISECONDS));
            result = BaseResultGenerator.success("SQL Queries Success.", mergeResult, statistics);
            result.setFormatType(QueryFormatEnum.JSON.getValue());
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

        if (dialect.equalsIgnoreCase(DBTypeEnum.CLICKHOUSE.getValue())) {
            testFile = Optional.of(new File(clickhouseWriter.buildPocSQL()));
        } else if (dialect.equalsIgnoreCase(DBTypeEnum.ENGINE.getValue())) {
            testFile = Optional.of(new File(engineWriter.buildPocSQL()));
        } else if (dialect.equalsIgnoreCase(DBTypeEnum.DRUID.getValue())) {
            testFile = Optional.of(new File(druidWriter.buildPocSQL()));
        } else {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(),
                    ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),QGWMessageConst.DIALECT_VALUE_ERROR));
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
            log.error("Get Poc SQL Fail:{}", e);
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(),
                    ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),e.getMessage()));
        }
        return result;
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
        List<Map> tcpSessionResult = getData(apiService.executeQuery(queryBuild(tcpSessionSql)));

        String tcpClientIpsSql = String.format(Objects.requireNonNull(env.getProperty("TOP_ENTITY_TCP_UNIQ_CLIENT_IPS")), datasource, timeInterval,
                datasource, timeInterval, entityConfigSource.getTopServerIPByTCPUniqClientIP());
        List<Map> tcpClientIpsResult = getData(apiService.executeQuery(queryBuild(tcpClientIpsSql)));

        String udpSessionSql = String.format(Objects.requireNonNull(env.getProperty("ENTITY_UDP_SESSION")), datasource, timeInterval,
                entityConfigSource.getTopServerIPByUDPBySession());
        List<Map> udpSessionResult = getData(apiService.executeQuery(queryBuild(udpSessionSql)));

        String udpClientIpsSql = String.format(Objects.requireNonNull(env.getProperty("TOP_ENTITY_UDP_UNIQ_CLIENT_IPS")), datasource, timeInterval,
                datasource, timeInterval, entityConfigSource.getTopServerIPByUDPUniqClientIP());
        List<Map> udpClientIpsResult = getData(apiService.executeQuery(queryBuild(udpClientIpsSql)));

        String tcpTotalSessionSql = String.format(Objects.requireNonNull(env.getProperty("TOTAL_ENTITY_TCP_SESSION")), datasource, timeInterval);
        List<Map> tcpTotalSessionResult = getData(apiService.executeQuery(queryBuild(tcpTotalSessionSql)));

        String tcpTotalClientIpsSql = String.format(Objects.requireNonNull(env.getProperty("TOTAL_ENTITY_TCP_UNIQ_CLIENT_IPS")), datasource, timeInterval);
        List<Map> tcpTotalClientIpResult = getData(apiService.executeQuery(queryBuild(tcpTotalClientIpsSql)));

        String udpTotalSessionSql = String.format(Objects.requireNonNull(env.getProperty("TOTAL_ENTITY_UDP_SESSION")), datasource, timeInterval);
        List<Map> udpTotalSessionResult = getData(apiService.executeQuery(queryBuild(udpTotalSessionSql)));

        String udpTotalClientIpsSql = String.format(Objects.requireNonNull(env.getProperty("TOTAL_ENTITY_UDP_UNIQ_CLIENT_IPS")), datasource, timeInterval);
        List<Map> udpTotalClientIpsResult = getData(apiService.executeQuery(queryBuild(udpTotalClientIpsSql)));

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
        List<Map> topSniResult = getData(apiService.executeQuery(queryBuild(topSniSql)));

        String totalSniSql = String.format(Objects.requireNonNull(env.getProperty("ENTITY_SNI_TOTAL")), datasource, timeInterval);
        List<Map> totalSniResult = getData(apiService.executeQuery(queryBuild(totalSniSql)));

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
        timeInterval.append("common_recv_time >= UNIX_TIMESTAMP('").append(intervals[0]).append("') AND common_recv_time< UNIX_TIMESTAMP('").append(intervals[1]).append("')");
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
            Map<String, Map<String, List<String>>> data = (Map<String, Map<String, List<String>>>) com.mesalab.common.utils.JsonMapper.fromJsonString(codeInfo.toString(), Map.class);
            return Maps.newHashMap(data.get("CDN"));
        }
        return Maps.newHashMap();
    }

    /**
     * 对各个数据库引擎的测试集SQL进行语法解析或验证
     * @param file
     * @param queryOption
     * @return
     */
    private Map<String, Map<String, String>> executeSyntaxCheckByDatasource(Optional<File> file, String queryOption) {
        Map<String, Map<String, String>> resultMap = Maps.newLinkedHashMap();
        Map<String, String> infoMap = Maps.newLinkedHashMap();
        if (file.isPresent() && file.get().isFile()) {
            List<String> rawLines = new FileReader(file.get()).readLines();
            int index = 0;
            for (String line : rawLines) {
                index++;
                if (Strings.isNullOrEmpty(line)) {
                    continue;
                }
                Stopwatch watch = Stopwatch.createStarted();
                BaseResult queryResult = getBaseResultByEngine(ApiParam.builder().option(queryOption).query(line).build());
                if (StringUtil.isEmpty(queryResult.getStatistics())) {
                    Map<String, Object> statistics = Maps.newLinkedHashMap();
                    statistics.put("elapsed", watch.elapsed(TimeUnit.MILLISECONDS));
                    queryResult.setStatistics(statistics);
                }
                processQueryResult(file, infoMap, null, index, queryResult);
            }
            resultMap.put(file.get().getName(), buildQueryTimeMetric(infoMap));
        } else {
            log.error("File is not exist or is a directory.");
        }
        return resultMap;

    }

    /**
     * 执行POC SQL测试集，并返回执行结果
     *
     * @param file   sql 查询语句列表
     * @param isSaved 是否保存查询结果
     * @param queryOption  SQL诊断方式
     * @return
     */
    private Map<String, Map<String, String>> executePocSqlByDatasource(Optional<File> file, boolean isSaved, String queryOption) {
        Map<String, Map<String, String>> resultMap = Maps.newLinkedHashMap();
        Map<String, String> infoMap = Maps.newLinkedHashMap();
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
                    processQueryResult(file, infoMap, writer, index, queryResult);
                }
            } else if (StringUtil.lowerCase(benchFileName).startsWith(CK_PREFIX)) {
                for (String line : rawLines) {
                    index++;
                    if (Strings.isNullOrEmpty(line)) {
                        continue;
                    }
                    BaseResult queryResult = generateClickHouseBaseResult(line);
                    processQueryResult(file, infoMap, writer, index, queryResult);
                }
            } else if (StringUtil.lowerCase(benchFileName).startsWith(HBASE_PREFIX)) {
                for (String line : rawLines) {
                    index++;
                    if (Strings.isNullOrEmpty(line)) {
                        continue;
                    }
                    BaseResult queryResult = generateHbaseBaseResult(line);
                    processQueryResult(file, infoMap, writer, index, queryResult);
                }
            } else if (StringUtil.lowerCase(benchFileName).startsWith(ENGINE_PREFIX)) {
                for (String line : rawLines) {
                    index++;
                    if (Strings.isNullOrEmpty(line)) {
                        continue;
                    }
                    BaseResult queryResult = getBaseResultByEngine(
                            ApiParam.builder().option(queryOption).format(QueryFormatEnum.CSV.getValue()).query(line).build());
                    processQueryResult(file, infoMap, writer, index, queryResult);
                }
            } else if (StringUtil.lowerCase(benchFileName).startsWith(ES_PREFIX)) {
                for (String line : rawLines) {
                    index++;
                    if (Strings.isNullOrEmpty(line)) {
                        continue;
                    }
                    BaseResult queryResult = generateElasticSearchBaseResult(line);
                    processQueryResult(file, infoMap, writer, index, queryResult);
                }
            }

            resultMap.put(file.get().getName(), buildQueryTimeMetric(infoMap));
        } else {
            log.error("File is not exist or is a directory.");
        }
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

    private void processQueryResult(Optional<File> file, Map<String, String> infoMap, FileWriter writer, int index, BaseResult queryResult) {
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
                    String.format(QGWErrorCode.SQL_EXECUTION_SERVER_EXCEPTION.getMessage(),file.get().getName()
                    + ",Query" + index + "," + queryResult.getStatus() + "," + queryResult.getMessage()));
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
        if (Integer.parseInt(results.get("status")) == ResultStatusEnum.SUCCESS.getCode()) {
            baseResult = BaseResultGenerator.generate(Integer.valueOf(results.get("status")), ResultCodeEnum.SUCCESS.getCode(), "ok",
                    (List<Object>) com.mesalab.common.utils.JsonMapper.fromJsonString(results.get("result"), Object.class), null, null, QueryFormatEnum.JSON.getValue());
            dataObject = baseResult.getData();
            List<Object> allResults = (List<Object>) dataObject;
            Map<String, Object> statistics = Maps.newLinkedHashMap();
            statistics.put("elapsed", watch.elapsed(TimeUnit.MILLISECONDS));
            statistics.put("rows_read", allResults.size());
            baseResult.setData(allResults);
            baseResult.setStatistics(statistics);
        } else {
            baseResult = BaseResultGenerator.generate(Integer.parseInt(results.get("status")), QGWErrorCode.SQL_BUILDER_EXCEPTION.getCode(), results.get("message"),
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
        Map<String, String> results = clickHouseExecuteHttpPost(sql);
        if (Integer.parseInt(results.get("status")) == ResultStatusEnum.SUCCESS.getCode()) {
            baseResult = BaseResultGenerator.generate(Integer.parseInt(results.get("status")), ResultCodeEnum.SUCCESS.getCode(), "ok",
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
            baseResult = BaseResultGenerator.generate(Integer.parseInt(results.get("status")), QGWErrorCode.SQL_BUILDER_EXCEPTION.getCode(), null,
                    results.get("result"), null, null, QueryFormatEnum.JSON.getValue());
            dataObject = baseResult.getData();
            baseResult.setStatus((baseResult.getStatus() < 500) || (baseResult.getStatus() > 400) ? ResultStatusEnum.SERVER_ERROR.getCode() : baseResult.getStatus());
            baseResult.setMessage("SQL Execution Error: ".concat(JsonPath.read(dataObject.toString(), "$.errorMessage")));
        }
        return baseResult;
    }

    private Map<String, String> clickHouseExecuteHttpPost(String sql) {
        String ckQuery = Encodes.urlEncode(sql);
        String queryURL = URLUtil.normalize(clickHouseHttpSource.getUrl() + "/?");
        StringBuilder queryParamBuilder = new StringBuilder("user=")
                .append(clickHouseHttpSource.getRealTimeAccountUserName()).append("&")
                .append("password=").append(clickHouseHttpSource.getRealTimeAccountPin()).append("&")
                .append("database=").append(clickHouseHttpSource.getDbName());
        ClickHouseHttpQuery clickHouseHttpQuery = new ClickHouseHttpQuery();
        clickHouseHttpQuery.setSocketTimeOut(httpConfig.getCkRealTimeAccountSocketTimeOut());
        clickHouseHttpQuery.setQueryParameter(queryParamBuilder.toString());
        log.info("DB Engine is :{}, Execute Http Query is: {}", DBTypeEnum.CLICKHOUSE.getValue(), com.mesalab.common.utils.JsonMapper.toJsonString(clickHouseHttpQuery));
        List<NameValuePair> values = URLEncodedUtils.parse(clickHouseHttpQuery.getQueryParameter(), Charset.forName("UTF-8"));
        return httpClientService.httpPost(queryURL + URLEncodedUtils.format(values, "utf-8"), sql.concat(" FORMAT ").concat(QueryFormatEnum.JSON.getValue().toUpperCase()), clickHouseHttpQuery.getSocketTimeOut());
    }

    private BaseResult generateElasticSearchBaseResult(String sql) {
        BaseResult baseResult;
        Object dataObject;
        Stopwatch watch = Stopwatch.createUnstarted();
        watch.start();
        Request request = new Request("POST", "/_sql?format=json");
        HashMap<String, Object> queryParam = Maps.newHashMap();
        queryParam.put("query", sql);
        request.setJsonEntity(JsonMapper.toJsonString(queryParam));
        Response response;
        try {
            response = restClient.performRequest(request);
            ResponseEntity<String> respEntity = new ResponseEntity<>(EntityUtils.toString(response.getEntity()), org.springframework.http.HttpStatus.OK);
            baseResult = BaseResultGenerator.generate(ResultStatusEnum.SUCCESS.getCode(), ResultCodeEnum.SUCCESS.getCode(), "ok",
                    (Map<String, Object>) JsonMapper.fromJsonString(respEntity.getBody(), Object.class), null, null, QueryFormatEnum.JSON.getValue());
        } catch (IOException e) {
            log.error("{} engine error: {}", DBTypeEnum.ELASTICSEARCH.getValue(), e);
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), QGWErrorCode.SQL_BUILDER_EXCEPTION.getCode(), e.getMessage());
        }
        dataObject = baseResult.getData();
        Map<String, Object> resultMap = (Map<String, Object>) dataObject;
        Map<String, Object> statistics = Maps.newLinkedHashMap();
        statistics.put("elapsed", watch.elapsed(TimeUnit.MILLISECONDS));
        baseResult.setData(resultMap);
        baseResult.setStatistics(statistics);
        return baseResult;
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
        try {
            Select select = (Select) CCJSqlParserUtil.parse(sql);
            select.getSelectBody().accept(SQLVisitorUtil.getVisitorOfEscapeMetadataWithDoubleQuote());
            return select.toString();
        } catch (JSQLParserException | RuntimeException e) {
            log.error("SQL Syntax Error: Converter SQL Syntax Error, SQL is:{}, Error is:{}", sql, e);
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), QGWErrorCode.SQL_SYNTAX_PARSE_EXCEPTION.getCode(),
                    String.format(QGWErrorCode.SQL_SYNTAX_PARSE_EXCEPTION.getMessage(),e.getMessage()));
        }
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
            BaseResult baseResult = getBaseResultByEngine(ApiParam.builder().option(QueryOptionEnum.REAL_TIME.getValue()).query("describe " + tableName).build());
            if (ResultStatusEnum.SUCCESS.getCode() != baseResult.getStatus()) {
                log.error("The Table[" + tableName + "] Structure query failed" + baseResult.getMessage());
                throw new QGWBusinessException(ResultStatusEnum.SERVER_ERROR.getCode(),
                        QGWErrorCode.SQL_EXECUTION_SERVER_EXCEPTION.getCode(),
                        String.format(QGWErrorCode.SQL_EXECUTION_SERVER_EXCEPTION.getMessage()," The Table[" + tableName + "] Structure query failed"));
            }
            List<Map<String, Object>> dbFields = JsonPath.read(JsonMapper.toJsonString(baseResult.getData()), "$.fields.*");

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

    private void checkCkIndexKey(String dbName, List<String> tables) {
        for (String tableName : tables) {
            Schema schema = metadataService.getSchemaByName(tableName);
            if (!dbName.equals(schema.getNamespace())) continue;
            BaseResult indexKeyResult = getBaseResultByEngine(ApiParam.builder().option(QueryOptionEnum.REAL_TIME.getValue()).query(String.format(Objects.requireNonNull(env.getProperty("TABLE_INDEX_KEY")), tableName, clickHouseHttpSource.getDbName())).build());
            if (ResultStatusEnum.SUCCESS.getCode() != indexKeyResult.getStatus()) {
                log.error("The Table[" + tableName + "] index key query failed" + indexKeyResult.getMessage());
                throw new QGWBusinessException(ResultStatusEnum.SERVER_ERROR.getCode(),
                        QGWErrorCode.SQL_EXECUTION_SERVER_EXCEPTION.getCode(),
                        String.format(QGWErrorCode.SQL_EXECUTION_SERVER_EXCEPTION.getMessage(), " The Table[" + tableName + "] index key query failed"));
            }
            List<Map<String, Object>> dataList = (List<Map<String, Object>>) indexKeyResult.getData();
            List<String> dbIndexKey = (List<String>) dataList.get(0).get("index_key");
            List<String> schemaIndexKey = metadataService.getIndexKey(tableName);

            List<String> onlyOnSchema = Lists.newArrayList();
            schemaIndexKey.stream().forEach(x -> {
                if (!dbIndexKey.contains(x)) {
                    onlyOnSchema.add(x);
                }
            });
            if (onlyOnSchema.size() > 0) {
                log.error("Schema index key greater than table index key,schema " + tableName + " exist index key :" + onlyOnSchema);
                throw new QGWBusinessException(ResultStatusEnum.SERVER_ERROR.getCode(),
                        QGWErrorCode.SQL_QUERY_FEDERATION_EXCEPTION.getCode(),
                        String.format(QGWErrorCode.SQL_QUERY_FEDERATION_EXCEPTION.getMessage(),
                                " Schema index key greater than table index key,schema " + tableName + " exist index key :" + onlyOnSchema));
            }

            List<String> onlyOnTable = Lists.newArrayList();
            dbIndexKey.stream().forEach(x -> {
                if (!schemaIndexKey.contains(x)) {
                    onlyOnTable.add(x);
                }
            });
            if (onlyOnTable.size() > 0) {
                log.error("Schema index key less than table index key,table " + tableName + " exist index key :" + onlyOnTable);
                throw new QGWBusinessException(ResultStatusEnum.SERVER_ERROR.getCode(),
                        QGWErrorCode.SQL_QUERY_FEDERATION_EXCEPTION.getCode(),
                        String.format(QGWErrorCode.SQL_QUERY_FEDERATION_EXCEPTION.getMessage(),
                                " Schema index key less than table index key,table " + tableName + " exist index key :" + onlyOnTable));
            }

            if (onlyOnSchema.size() == 0 && onlyOnTable.size() == 0) {
                Iterator<String> dbIterator = dbIndexKey.iterator();
                Iterator<String> schemaIterator = schemaIndexKey.iterator();
                while (dbIterator.hasNext() && schemaIterator.hasNext()) {
                    if (!dbIterator.next().equals(schemaIterator.next())) {
                        log.error("Schema index key order is inconsistent," + tableName + " schema index key order should be :" + dbIndexKey);
                        throw new QGWBusinessException(ResultStatusEnum.SERVER_ERROR.getCode(),
                                QGWErrorCode.SQL_QUERY_FEDERATION_EXCEPTION.getCode(),
                                String.format(QGWErrorCode.SQL_QUERY_FEDERATION_EXCEPTION.getMessage(),
                                        "Schema index key order is inconsistent," + tableName + " schema index key order should be :" + dbIndexKey));
                    }
                }
            }
        }
    }


    private BaseResult getBaseResultByEngine(ApiParam apiParam) {
        return apiService.executeQuery(apiParam);
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
