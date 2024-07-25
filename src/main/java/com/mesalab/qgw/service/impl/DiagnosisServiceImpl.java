package com.mesalab.qgw.service.impl;

import cn.hutool.core.io.file.FileReader;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.*;
import com.mesalab.common.nacos.NacosConfig;
import com.mesalab.qgw.benchmark.DialectWriter;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.qgw.model.basic.*;
import com.mesalab.qgw.service.DiagnosisService;
import com.mesalab.qgw.service.DatabaseService;
import com.mesalab.services.common.property.SqlPropertySourceFactory;
import com.geedgenetworks.utils.*;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service("diagnosisService")
@PropertySource(value = "classpath:http-sql-template.sql", factory = SqlPropertySourceFactory.class)
public class DiagnosisServiceImpl implements DiagnosisService, EnvironmentAware {
    private static final Log log = LogFactory.get();
    private static final String SCHEMA_PREFIX = "Schema/";

    @Autowired
    private DialectWriter dialectWriter;
    @Autowired
    private ClickHouseHttpSource clickHouseHttpSource;
    @Autowired
    private DruidIoHttpSource druidIoHttpSource;
    @Autowired
    private HBaseAPISource hBaseAPISource;
    @Autowired
    private DatabaseService databaseService;
    @Autowired
    private NacosConfig nacosConfig;
    @Autowired
    private ConfigService systemConfigService;
    @Autowired
    HttpConfig httpConfig;

    private Environment env;
    private final static String DATA_ID = "version.json";


    @Override
    public JSONObject getVersionInfo() {
        JSONObject version = null;
        try {
            String content = systemConfigService.getConfig(DATA_ID, nacosConfig.getGroup(), 3000);
            version = JSONObject.parseObject(content);
        } catch (NacosException e) {
            log.error("NacosException:{}", e.getMessage());
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
            Map<String, Map<String, String>> clickhouseResult = parseSchemaByDatasource(clickHouseHttpSource.getDbName(), databaseService.getAllTable());
            Map<String, Map<String, String>> druidResult = parseSchemaByDatasource(druidIoHttpSource.getDbname(), databaseService.getAllTable());
            Map<String, Map<String, String>> hbaseResult = parseSchemaByDatasource(hBaseAPISource.getDbName(), databaseService.getAllTable());

            data.add(clickhouseResult.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
            data.add(druidResult.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
            data.add(hbaseResult.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));

            long executeTime = watch.elapsed(TimeUnit.MILLISECONDS);
            statistics.put("elapsed", executeTime);
            result = BaseResultGenerator.success("Schema Validation Success.", data, statistics);
            result.setOutputMode(OutputMode.JSON.getValue());
        } catch (RuntimeException e) {
            log.error("Schema Validation Fail:{}", e);
            throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(),
                    CommonErrorCode.SQL_EXECUTION_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.SQL_EXECUTION_EXCEPTION.getMessage(), e.getMessage()));

        }
        return result;
    }

    @Override
    public BaseResult getPocSQL(String dialect, Integer queryNo) {
        BaseResult result;
        Optional<File> testFile;
        Map<String, Object> statistics = Maps.newLinkedHashMap();
        Map<String, String> dialectResult = Maps.newHashMap();
        try {
            Stopwatch watch = Stopwatch.createStarted();
            String path = new File("").getCanonicalPath()
                    + File.separator + "benchmark" + File.separator + dialect + "_queries" + "_"
                    + DateUtils.getCurrentDate(DateUtils.YYYYMMDD) + ".sql";
            if (Files.exists(Paths.get(path))) {
                dialectResult = getPocSqlByDatasource(Optional.of(new File(path)), queryNo);
            } else {
                testFile = Optional.of(new File(dialectWriter.buildPocSQL(Lists.newLinkedList(), dialect, null)));
                if (!testFile.get().isFile()) {
                    log.warn("UnKnow File " + testFile.get());
                    return null;
                }
                dialectResult = getPocSqlByDatasource(testFile, queryNo);
            }
            statistics.put("elapsed", watch.elapsed(TimeUnit.MILLISECONDS));
            result = BaseResultGenerator.success("Get SQL Success.", dialectResult, statistics);
            result.setOutputMode(OutputMode.JSON.getValue());
        } catch (RuntimeException | IOException e) {
            log.error("Get Poc SQL Fail:{}", e);
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(),
                    CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), e.getMessage()));
        }
        return result;
    }


    public List<Map> getData(BaseResult baseResult) {
        List<Map> dataList = (List<Map>) baseResult.getData();
        return dataList;
    }


    private Map<String, String> getPocSqlByDatasource(Optional<File> file, Integer queryNo) {
        Map<String, String> infoMap = Maps.newLinkedHashMap();
        List<String> rawLines = file.isPresent() ? new FileReader(file.get()).readLines() : Lists.newArrayList();
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
            LinkedHashMap<String, Object> schemaMap = databaseService.getSchemaByName(tableName);
            if (!dbName.equals(schemaMap.get("namespace"))) continue;
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
