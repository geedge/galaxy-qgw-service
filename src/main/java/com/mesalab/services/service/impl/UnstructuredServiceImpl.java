package com.mesalab.services.service.impl;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.clearspring.analytics.util.Lists;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Maps;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.QueryFormatEnum;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.ClickHouseHttpSource;
import com.mesalab.qgw.model.api.EngineConfigSource;
import com.mesalab.qgw.service.ApiService;
import com.mesalab.qgw.service.MetadataService;
import com.mesalab.services.common.dsl.ComDSLObject;
import com.mesalab.services.common.enums.UnstructuredDataType;
import com.mesalab.services.common.property.SqlPropertySourceFactory;
import com.mesalab.services.service.UnstructuredService;
import com.zdjizhi.utils.JsonMapper;
import com.zdjizhi.utils.StringUtil;
import org.apache.avro.Schema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service("UnstructuredService")
@PropertySource(value = "classpath:http-sql-template.sql", factory = SqlPropertySourceFactory.class)
public class UnstructuredServiceImpl implements UnstructuredService, EnvironmentAware {

    private static final Log log = LogFactory.get();
    private final static Map<String, String> fileType = Maps.newHashMap();
    private static final String COMMONFILE = "common";

    static {
        fileType.put("common_packet_capture_file", UnstructuredDataType.PCAP.getType());
        fileType.put("rtp_pcap_path", UnstructuredDataType.PCAP.getType());
        fileType.put("packet_url", UnstructuredDataType.PCAP.getType());
        fileType.put("mail_eml_file", UnstructuredDataType.MAIL.getType());
        fileType.put("http_request_body", UnstructuredDataType.HTTP.getType());
        fileType.put("http_response_body", UnstructuredDataType.HTTP.getType());
    }

    @Autowired
    private MetadataService metadataService;
    @Autowired
    private ClickHouseHttpSource clickHouseHttpSource;
    @Autowired
    private EngineConfigSource engineConfigSource;
    @Autowired
    private ApiService apiService;
    private Environment env;

    @Override
    public Map<String, Object> getUnstructuredFields() {
        Map<String, Object> fileTypeInSchema = Maps.newHashMap();
        List<String> allTable = metadataService.getAllTable();
        for (String tableName : allTable) {
            Schema schema = metadataService.getSchemaByName(tableName);
            Map schemaMap = (Map) JsonMapper.fromJsonString(schema.toString(), Map.class);
            List<String> fieldList = new ArrayList<>();
            if (!clickHouseHttpSource.getDbName().equals(schema.getNamespace())) continue;
            List<Map> fields = (List<Map>) schemaMap.get("fields");
            for (Map field : fields) {
                Map schemaDoc = (Map) JsonMapper.fromJsonString(field.get("doc").toString(), Map.class);
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
                    String fileTypeStr = StringUtil.isNotEmpty(fileType.get(field)) ? fileType.get(field).toString() : COMMONFILE;
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
        Map<String, Object> fileTypeInSchema = getUnstructuredFields();
        List<Map> dataList = Lists.newArrayList();
        String dataSource = dslObject.getQuery().getDataSource();
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
        statistics.put("result_size", dataList.toString().getBytes().length);
        baseResult = BaseResultGenerator.success("ok", dataList, statistics);
        return baseResult;
    }

    private List<Map> getData(Map<String, Object> fileTypeMap, int limit, String param, ComDSLObject dslObject, String dataSource) {
        List<Map> dataResult = Lists.newArrayList();
        Map<String, List<String>> tableFileType = (Map<String, List<String>>) fileTypeMap.get(dataSource);
        if (tableFileType.containsKey(param)) {
            List<String> listFields = tableFileType.get(param);
            for (String field : listFields) {
                String sql = String.format(Objects.requireNonNull(env.getProperty("UNSTRUCTURED_DATA_PATH")), field, dataSource, generateWhereOfTime(dslObject.getQuery()), field, limit);
                BaseResult result = apiService.executeQuery(queryBuild(sql));
                if (!result.isSuccess()) {
                    log.error("Query unstructured data status error: {}", result.getMessage());
                    throw new QGWBusinessException(ResultStatusEnum.SERVER_ERROR.getCode(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), "Query unstructured data status error: " + result.getMessage());
                }
                List<Map> datalist = (List<Map>) result.getData();
                for (Map data : datalist) {
                    data.put("log_type", dataSource);
                    dataResult.add(data);
                }
            }
        }
        return dataResult;
    }

    private String generateWhereOfTime(ComDSLObject.Query query) {
        String[] intervals = getIntervals(query.getParameters().getIntervals());
        String whereOfTime = "common_recv_time >=  UNIX_TIMESTAMP('" + intervals[0] + "') AND common_recv_time <  UNIX_TIMESTAMP('" + intervals[1] + "')";
        return whereOfTime;
    }

    private String[] getIntervals(List<String> intervals) {
        return intervals.get(0).split("/");
    }

    private static <T> Predicate<T> distinctByValue(Function<? super T, Object> keyExtractor) {
        Map<Object, Boolean> seen = new ConcurrentHashMap<>();
        return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
    }


    private ApiParam queryBuild(String sql) {
        return ApiParam.builder().format(QueryFormatEnum.JSON.getValue()).query(sql).build();
    }


    @Override
    public void setEnvironment(Environment environment) {
        this.env = environment;
    }
}
