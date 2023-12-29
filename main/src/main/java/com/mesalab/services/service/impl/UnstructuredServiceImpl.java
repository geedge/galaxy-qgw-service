package com.mesalab.services.service.impl;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson2.JSON;
import com.clearspring.analytics.util.Lists;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Maps;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.OutputModeEnum;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.qgw.model.basic.QueryProfile;
import com.mesalab.qgw.model.basic.ClickHouseHttpSource;
import com.mesalab.qgw.model.basic.EngineConfigSource;
import com.mesalab.qgw.service.QueryService;
import com.mesalab.qgw.service.DatabaseService;
import com.mesalab.services.common.dsl.ComDSLObject;
import com.mesalab.services.common.enums.UnstructuredDataType;
import com.mesalab.services.common.property.SqlPropertySourceFactory;
import com.mesalab.services.service.UnstructuredService;
import com.geedgenetworks.utils.StringUtil;
import org.apache.avro.Schema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;


import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service("UnstructuredService")
@PropertySource(value = "classpath:http-sql-template.sql", factory = SqlPropertySourceFactory.class)
public class UnstructuredServiceImpl implements UnstructuredService, EnvironmentAware {

    private static final Log log = LogFactory.get();
    public final static Map<String, String> fileType = Maps.newHashMap();
    public static final String OTHER_FILE = "other";
    public static final String[] tables = new String[]{"session_record", "security_event", "monitor_event", "transaction_record", "voip_record", "proxy_event", "dos_event", "assessment_event"};
    static {
        fileType.put("common_packet_capture_file", UnstructuredDataType.PCAP.getType());
        fileType.put("packet_capture_file", UnstructuredDataType.PCAP.getType());
        fileType.put("rtp_pcap_path", UnstructuredDataType.PCAP.getType());
        fileType.put("packet_url", UnstructuredDataType.PCAP.getType());
        fileType.put("mail_eml_file", UnstructuredDataType.MAIL.getType());
        fileType.put("http_request_body", UnstructuredDataType.HTTP.getType());
        fileType.put("http_response_body", UnstructuredDataType.HTTP.getType());
    }

    @Autowired
    private DatabaseService databaseService;
    @Autowired
    private ClickHouseHttpSource clickHouseHttpSource;
    @Autowired
    private EngineConfigSource engineConfigSource;
    @Autowired
    private QueryService queryService;
    private Environment env;

    @Override
    public Map<String, Object> getUnstructuredFields() {
        Map<String, Object> fileTypeInSchema = Maps.newHashMap();
        List<String> allTable = databaseService.getAllTable();
        for (String tableName : allTable) {
            Schema schema = databaseService.getSchemaByName(tableName);
            Map schemaMap = JSON.parseObject(schema.toString(), Map.class);
            List<String> fieldList = new ArrayList<>();
            if (!clickHouseHttpSource.getDbName().equals(schema.getNamespace())) continue;
            List<Map> fields = (List<Map>) schemaMap.get("fields");
            for (Map field : fields) {
                Map schemaDoc = JSON.parseObject(field.get("doc").toString(), Map.class);
                if (schemaDoc.containsKey("constraints") && schemaDoc.get("visibility").equals("enabled")) {
                    Map<String, String> SchemaConstraints = (Map<String, String>) schemaDoc.get("constraints");
                    String typeStr = SchemaConstraints.containsKey("type") ? SchemaConstraints.get("type") : StringUtil.EMPTY;
                    if (typeStr.equals("file") || typeStr.equals("files")) {
                        fieldList.add((String) field.get("name"));
                    }
                }
            }

            Map<String, List<String>> fileTypeMap = Maps.newHashMap();
            if (fieldList.size() != 0) {
                for (String field : fieldList) {
                    String fileTypeStr = StringUtil.isNotEmpty(fileType.get(field)) ? fileType.get(field).toString() : OTHER_FILE;
                    if (fileTypeMap.containsKey(fileTypeStr)) {
                        List<String> preList = fileTypeMap.get(fileTypeStr);
                        preList.add(field);
                        fileTypeMap.put(fileTypeStr, preList);
                    } else {
                        List<String> newList = new ArrayList<>();
                        newList.add(field);
                        fileTypeMap.put(fileTypeStr, newList);
                    }
                }
            }
            if (!fileTypeMap.isEmpty()) {
                fileTypeInSchema.put(tableName, fileTypeMap);
            }
        }
        return fileTypeInSchema;
    }

    @Override
    public BaseResult getUnstructuredData(String param, ComDSLObject dslObject) {
        Stopwatch watch = Stopwatch.createStarted();
        List<Map> dataList = Lists.newArrayList();
        String dataSource = dslObject.getQuery().getDataSource();
        validationDataSource(dataSource);
        Map<String, Object> fileTypeInSchema = getUnstructuredFields();
        if (!fileTypeInSchema.containsKey(dataSource) && !dataSource.equals(clickHouseHttpSource.getDbName())) {
            return build(dataList, watch);
        }
        int limit = StringUtil.isBlank(dslObject.getQuery().getLimit()) ? engineConfigSource.getDefaultResultNum() : Integer.parseInt(dslObject.getQuery().getLimit());
        if (!param.equalsIgnoreCase(UnstructuredDataType.ALL.getType())) {
            if (!dataSource.equals(clickHouseHttpSource.getDbName())) {
                dataList = getData(fileTypeInSchema, limit, param, dslObject, dataSource);
            } else {
                for (String tableName : fileTypeInSchema.keySet()) {
                    dataList.addAll(getData(fileTypeInSchema, limit, param, dslObject, tableName));
                }
            }
        } else {
            if (!dataSource.equals(clickHouseHttpSource.getDbName())) {
                Map<String, List<String>> fileType = (Map<String, List<String>>) fileTypeInSchema.get(dataSource);
                for (String s : fileType.keySet()) {
                    dataList.addAll(getData(fileTypeInSchema, limit, s, dslObject, dataSource));
                }
            } else {
                for (String tableName : fileTypeInSchema.keySet()) {
                    Map<String, Object> fileType = (Map<String, Object>) fileTypeInSchema.get(tableName);
                    for (String s : fileType.keySet()) {
                        dataList.addAll(getData(fileTypeInSchema, limit, s, dslObject, tableName));
                    }
                }
            }
        }

        List<Map> distinctList = dataList.stream()
                .filter(distinctByValue(x -> x.get("file_path")))
                .collect(Collectors.toList());

        distinctList = distinctList.size() > limit ? distinctList.subList(0, Integer.parseInt(dslObject.getQuery().getLimit())) : distinctList;
        return build(distinctList, watch);
    }

    private BaseResult build(List<Map> dataList, Stopwatch watch) {
        BaseResult baseResult;
        Map statistics = Maps.newHashMap();
        statistics.put("elapsed", watch.elapsed(TimeUnit.MILLISECONDS));
        statistics.put("result_rows", dataList.size());
        statistics.put("result_bytes", dataList.toString().getBytes().length);
        baseResult = BaseResultGenerator.success("ok", dataList, statistics);
        return baseResult;
    }

    private List<Map> getData(Map<String, Object> fileTypeMap, int limit, String param, ComDSLObject dslObject, String dataSource) {
        List<Map> dataResult = Lists.newArrayList();
        Map<String, List<String>> tableFileType = (Map<String, List<String>>) fileTypeMap.get(dataSource);
        if (tableFileType.containsKey(param)) {
            List<String> listFields = tableFileType.get(param);
            for (String field : listFields) {
                String sql = String.format(
                        Arrays.asList(tables).contains(dataSource) ? Objects.requireNonNull(env.getProperty("UNSTRUCTURED_DATA_PATH")) : Objects.requireNonNull(env.getProperty("UNSTRUCTURED_DATA_PATH_OLD"))
                        , field, dataSource
                        , generateWhereOfTime(Arrays.asList(tables).contains(dataSource) ? "recv_time" : "common_recv_time", dslObject.getQuery())
                        , field, limit);
                BaseResult result = queryService.executeQuery(queryBuild(sql));
                if (!result.isSuccess()) {
                    log.error("Query unstructured data status error: {}", result.getMessage());
                    throw new QGWBusinessException(ResultStatusEnum.SERVER_ERROR.getCode(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), "Query unstructured data status error: " + result.getMessage());
                }
                List<Map> datalist = (List<Map>) result.getData();
                for (Map data : datalist) {
                    String fileTypeStr = StringUtil.isNotEmpty(fileType.get(field)) ? fileType.get(field).toString() : OTHER_FILE;
                    data.put("file_type", fileTypeStr);
                    data.put("log_type", dataSource);
                    dataResult.add(data);
                }
            }
        }
        return dataResult;
    }

    private String generateWhereOfTime(String timeKey, ComDSLObject.Query query) {
        String[] intervals = getIntervals(query.getParameters().getIntervals());
        String whereOfTime = timeKey + " >=  UNIX_TIMESTAMP('" + intervals[0] + "') AND " + timeKey + " <  UNIX_TIMESTAMP('" + intervals[1] + "')";
        return whereOfTime;
    }

    private String[] getIntervals(List<String> intervals) {
        return intervals.get(0).split("/");
    }

    private static <T> Predicate<T> distinctByValue(Function<? super T, Object> keyExtractor) {
        Map<Object, Boolean> seen = new ConcurrentHashMap<>();
        return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
    }


    private QueryProfile queryBuild(String sql) {
        return QueryProfile.builder().format(OutputModeEnum.JSON.getValue()).query(sql).build();
    }

    private void validationDataSource(String dataSource) {
        List<String> allTable = databaseService.getAllTable();
        if (!allTable.contains(dataSource) && !dataSource.equals(clickHouseHttpSource.getDbName())) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), QGWMessageConst.DATASOURCE_NOT_EXIST));
        }
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.env = environment;
    }
}
