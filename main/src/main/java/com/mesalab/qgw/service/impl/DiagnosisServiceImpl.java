package com.mesalab.qgw.service.impl;

import cn.hutool.core.io.file.FileReader;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson2.JSON;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mesalab.common.configuration.HttpConfig;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.*;
import com.mesalab.common.nacos.NacosConfig;
import com.mesalab.qgw.benchmark.DialectWriter;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service("diagnosisService")
@PropertySource(value = "classpath:http-sql-template.sql", factory = SqlPropertySourceFactory.class)
public class
DiagnosisServiceImpl implements DiagnosisService, EnvironmentAware {
    private static final Log log = LogFactory.get();
    private static final String SCHEMA_PREFIX = "Schema/";
    private static final String HBASE_DBNAME2 = "tsg_galaxy";

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
    private NacosConfig nacosConfig;
    @Autowired
    private ConfigService systemConfigService;
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
