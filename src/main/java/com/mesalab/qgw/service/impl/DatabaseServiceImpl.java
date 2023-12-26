package com.mesalab.qgw.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONException;
import cn.hutool.json.JSONUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.annotation.NacosValue;
import com.alibaba.nacos.api.config.listener.AbstractListener;
import com.alibaba.nacos.api.exception.NacosException;
import com.geedgenetworks.utils.DateUtils;
import com.geedgenetworks.utils.Encodes;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayway.jsonpath.JsonPath;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.*;
import com.mesalab.common.utils.sqlparser.SQLHelper;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.common.nacos.NacosConfig;
import com.mesalab.common.nacos.NacosConst;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.qgw.model.basic.HttpConfig;
import com.mesalab.qgw.model.basic.JobAdminHttpSource;
import com.mesalab.qgw.model.basic.SQLQueryContext;
import com.mesalab.qgw.model.basic.ClickHouseHttpSource;
import com.mesalab.qgw.model.job.ExecutorParam;
import com.mesalab.qgw.model.job.StorageDeletionInfo;
import com.mesalab.qgw.model.job.XxlJobInfo;
import com.mesalab.qgw.model.metadata.MetadataBean;
import com.mesalab.qgw.model.metadata.MetadataConfig;
import com.mesalab.qgw.service.DatabaseService;
import com.mesalab.qgw.service.SQLSyncQueryService;
import com.geedgenetworks.utils.StringUtil;
import com.mesalab.services.common.property.SqlPropertySourceFactory;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.avro.Schema;
import org.apache.avro.SchemaParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cglib.beans.BeanMap;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.util.*;

/**
 * @Date: 2021-03-11 15:48
 * @Author : liuyongqiang
 * @ClassName : DatabaseServiceImpl
 * @Description : 获取Nacos注册中心元数据实现类
 */
@Service("databaseService")
@PropertySource(value = "classpath:http-sql-template.sql", factory = SqlPropertySourceFactory.class)
public class DatabaseServiceImpl implements DatabaseService, EnvironmentAware {

    private static final Log log = LogFactory.get();
    @Autowired
    private NacosConfig nacosConfig;
    @Autowired
    private ConfigService systemConfigService;
    @Autowired
    private ConfigService pubConfigService;
    @Autowired
    private ClickHouseHttpSource clickHouseHttpSource;
    @Autowired
    private SQLSyncQueryService sqlSyncQueryService;
    @Autowired
    public MetadataConfig metadataConfig;
    @Autowired
    private JobAdminHttpSource jobAdminHttpSource;
    @Autowired
    private HttpClientService httpClientService;
    @Autowired
    HttpConfig httpConfig;
    @Autowired
    Environment environment;
    @NacosValue(value = "${switch.version.schema}", autoRefreshed = true)
    private String schemaVersion;

    private Map<String, Schema> schemaCache = Maps.newHashMap();
    private Map<String, Object> originalCache = Maps.newHashMap();
    private final static String KEY_REF = "$ref";
    private final static String KEY = "key";
    private final static String VALUE = "value";
    private final static String CODE = "code";
    private final static String INDEX_KEY = "index_key";
    private Environment env;
    public final static String queryIdSeparator = ":";

    private Map<String, String> headers = Maps.newHashMap();

    @PostConstruct
    public void init() {
        addMetadataListener();
        log.info("Initializing Schema Tables Complete");
    }

    @Override
    public Map<String, Object> getSchemaInfo(String type, String name, boolean displayStorageSize) {
        if (MetadataType.TABLES.getValue().equals(type)) {
            return loadTables(name);
        } else if (MetadataType.FIELDS.getValue().equals(type)) {
            return loadFields(name, displayStorageSize);
        }
        throw new QGWBusinessException(HttpStatusCodeEnum.NOT_FOUND.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.SCHEMA_URL_NOT_FOUND));
    }

    @Override
    public String getPartitionKey(String tableName) {
        String partitionKey = StringUtil.EMPTY;
        Schema schema = getSchemaByName(tableName);
        Map doc = (Map) JSON.parseObject(schema.getDoc(), Map.class);
        if (StringUtil.isNotEmpty(doc)) {
            Object partition_key = doc.get("partition_key");
            partitionKey = StringUtil.isEmpty(partition_key) ? StringUtil.EMPTY : partition_key.toString();
        }
        return partitionKey;
    }

    @Override
    public List<String> getIndexKey(String tableName) {
        Map schemaJsonMap = loadFields(tableName, false);
        if (StringUtil.isEmpty(schemaJsonMap) || StringUtil.isEmpty(schemaJsonMap.get("doc"))) {
            return Lists.newArrayList();
        }
        Map doc = (Map) schemaJsonMap.get("doc");
        Object indexKey = doc.get(INDEX_KEY);
        return StringUtil.isEmpty(indexKey) ? Lists.newArrayList() : (List<String>) indexKey;
    }

    @Override
    public String getValueByKeyInSchemaDoc(String tableName, String key) {
        Schema schema = getSchemaByName(tableName);
        Map doc = (Map) JSON.parseObject(schema.getDoc(), Map.class);
        if (StringUtil.isNotEmpty(doc)) {
            Object value = doc.get(key);
            if (value instanceof Map) {
                return JSONUtil.toJsonStr(JSONUtil.parseObj(value));
            } else {
                return StringUtil.isEmpty(value) ? StringUtil.EMPTY : value.toString();
            }
        }
        return StringUtil.EMPTY;
    }

    @Override
    public String getDBEngineByTableName(String tableName) {
        Schema schema = getSchemaByName(tableName);
        String data = StringUtil.EMPTY;
        if (StringUtil.isEmpty(schema)) {
            return data;
        }
        for (MetadataBean meta : metadataConfig.getMetadata()) {
            if (meta.getTables().contains(tableName)) {
                return getDBTypeByGroup(meta.getGroup());
            }
        }
        return data;
    }

    private String getDBTypeByGroup(String group) {
        String type;
        switch (group) {
            case "CLICKHOUSE_GROUP":
                type = DBEngineType.CLICKHOUSE.getValue();
                break;
            case "HBASE_GROUP":
                type = DBEngineType.HBASE.getValue();
                break;
            case "DRUID_GROUP":
                type = DBEngineType.DRUID.getValue();
                break;
            default:
                type = StringUtil.EMPTY;
        }
        return type;
    }

    @Override
    public String getDBNameByTableName(String tableName) {
        Schema schema = getSchemaByName(tableName);
        return StringUtil.isNotEmpty(schema) ? schema.getNamespace() : clickHouseHttpSource.getDbName();
    }

    @Override
    public List<String> getAllTable() {
        List<String> list = new ArrayList<>();
        metadataConfig.getMetadata().forEach(o -> {
            if (StringUtil.isNotEmpty(o.getTables())) {
                list.addAll(o.getTables());
            }
        });
        return list;
    }

    @Override
    public Schema getSchemaByName(String tableName) {
        if (!getAllTable().contains(tableName)) {
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(),
                    CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(),
                    tableName + ":"+QGWMessageConst.TABLE_NOT_EXIST));
        }
        Schema schema;
        if (!StrUtil.isBlankIfStr(schema = schemaCache.get(tableName))) {
            return schema;
        }
        try {
            String content = systemConfigService.getConfig(tableName.concat(NacosConst.JSON_SUFFIX), nacosConfig.getGroup(), 3000);
            if (StrUtil.isBlankIfStr(content)) {
                throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(),
                        CommonErrorCode.PARAMETER_ERROR.getCode(),
                        String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(),tableName + ":"+QGWMessageConst.QUERY_SCHEMA_ERROR));
            }
            schemaCache.put(tableName, schema = parseSchema(content));
            systemConfigService.addListener(tableName.concat(NacosConst.JSON_SUFFIX), nacosConfig.getGroup(), new AbstractListener() {
                @Override
                public void receiveConfigInfo(String configInfo) {
                    log.info("ReceiveConfigInfo Schema Fields {}", configInfo);
                    schemaCache.remove(tableName);
                    schemaCache.put(tableName, parseSchema(configInfo));
                }
            });
        } catch (NacosException e) {
            log.error("NacosException:{}", e);
        }
        return schema;
    }

    @Override
    public BaseResult updateSchema(String name, Map<String, Object> paramMap) {
        try {
            String content = systemConfigService.getConfig(name.concat(".json"), nacosConfig.getGroup(), 3000);
            updateSchema(name, paramMap, content);
            String indexTable = getValueByKeyInSchemaDoc(name, "index_table");
            if (StringUtil.isNotBlank(indexTable)) {
                List<String> list = Splitter.on(",").omitEmptyStrings().splitToList(indexTable);
                for (String item : list) {
                    String config = systemConfigService.getConfig(item.concat(".json"), nacosConfig.getGroup(), 3000);
                    updateSchema(item, paramMap, config);
                }
            }
            return BaseResultGenerator.success4Message("ok");
        } catch (NacosException e) {
            throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                    e.getMessage());
        }
    }

    @Override
    public BaseResult getStorageQuota() {
        //封装sql进行查询：Analytic Logs、Files、Traffic Logs
        SQLQueryContext param = new SQLQueryContext();
        param.setRawSQL(env.getProperty("SYSTEM_STORAGE_QUOTA"));
        BaseResult result = sqlSyncQueryService.executeQuery(param);
        return resetBaseResult(result, "type");
    }

    @Override
    public BaseResult dailyTrendOfStorage(String searchStartTime, String searchEndTime) {
        Date currentDate = DateUtils.convertStringToDate(DateUtils.getCurrentDate(), DateUtils.YYYY_MM_DD);

        if (StringUtil.isBlank(searchStartTime) && StringUtil.isBlank(searchEndTime)) {
            searchStartTime = DateUtils.getFormatDate(DateUtils.getSomeDate(currentDate, -7), DateUtils.YYYY_MM_DD_HH24_MM_SS);
            searchEndTime = DateUtils.getFormatDate(currentDate, DateUtils.YYYY_MM_DD_HH24_MM_SS);
        }

        if (StringUtil.isNotBlank(searchStartTime) && StringUtil.isNotBlank(searchEndTime)) {
            SQLQueryContext param = new SQLQueryContext();
            String sql = String.format(env.getProperty("SYSTEM_DAILY_TREND_OF_STORAGE"), searchStartTime, searchEndTime);
            param.setRawSQL(sql);
            BaseResult result = sqlSyncQueryService.executeQuery(param);
            List<Map> data = (List<Map>) result.getData();
            data.forEach(o -> o.put("type", LogType.getLabelByValue(String.valueOf(o.get("type")))));
            return result;
        } else {
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.START_TIME_AND_END_TIME_NOT_NULL));
        }
    }

    @Override
    public BaseResult deleteStorage(List<StorageDeletionInfo> list) {
        if (!logTypeValid(list)) {
            return BaseResultGenerator.failure(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(),"Match failed, please check log type!"));
        }

        preProcessOfLogType(list);

        if (jobIsBusy(list)) {
            return BaseResultGenerator.failure(HttpStatusCodeEnum.LOCKED.getCode(),  CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(),"The task is busy, please try later!"));
        }

        BaseResult baseResult = null;
        for (StorageDeletionInfo info : list) {
            baseResult = executeDeleteStorageJob(info);
        }
        return baseResult;
    }
    @Override
    public BaseResult getDeleteStorageStatus(String logType) {
        setCookie();
        List<StorageDeletionInfo> list = new ArrayList<>();
        if (StringUtil.isBlank(logType)) {

            StorageDeletionInfo trafficInfo = getStorageDeletionInfoByHandler(LogType.TRAFFIC_LOGS.getValue(), JobHandlerOption.DELETE_TRAFFIC_DATA_JOB_HANDLER.getValue());
            list.add(trafficInfo);

            StorageDeletionInfo reportInfo = getStorageDeletionInfoByHandler(LogType.REPORT_AND_METRICS.getValue(), JobHandlerOption.DELETE_REPORT_AND_METRICS_DATA_JOB_HANDLER.getValue());
            list.add(reportInfo);

            StorageDeletionInfo fileInfo = getStorageDeletionInfoByHandler(LogType.FILES.getValue(), JobHandlerOption.DELETE_FILES_JOB_HANDLER.getValue());
            list.add(fileInfo);
        } else if (LogType.TRAFFIC_LOGS.getValue().equalsIgnoreCase(logType)) {
            StorageDeletionInfo trafficInfo = getStorageDeletionInfoByHandler(logType, JobHandlerOption.DELETE_TRAFFIC_DATA_JOB_HANDLER.getValue());
            list.add(trafficInfo);
        } else if (LogType.REPORT_AND_METRICS.getValue().equalsIgnoreCase(logType)) {
            StorageDeletionInfo reportInfo = getStorageDeletionInfoByHandler(logType, JobHandlerOption.DELETE_REPORT_AND_METRICS_DATA_JOB_HANDLER.getValue());
            list.add(reportInfo);
        } else if (LogType.FILES.getValue().equalsIgnoreCase(logType)) {
            StorageDeletionInfo reportInfo = getStorageDeletionInfoByHandler(logType, JobHandlerOption.DELETE_FILES_JOB_HANDLER.getValue());
            list.add(reportInfo);
        } else {
            return BaseResultGenerator.failure(HttpStatusCodeEnum.SERVER_ERROR.getCode(), "No matching log type: " + logType);
        }
        list.forEach(o -> o.setType(LogType.getLabelByValue(o.getType())));
        return BaseResultGenerator.success("ok", list);
    }

    @Override
    public String getCustomQueryId(String resultId, String query) {
        String tableName = SQLHelper.getTableName(query).get(0);
        String dbType = getDBEngineByTableName(tableName);
        return DigestUtil.md5Hex(dbType) + queryIdSeparator + DigestUtil.md5Hex(resultId + query.trim());
    }

    private boolean updateSchema(String name, Map<String, Object> paramMap, String content) throws NacosException {
        if (StringUtil.isNotEmpty(content)) {
            Map<String, Object> data = JSON.parseObject(content, Map.class);
            data.put("doc", rewriteDoc("ttl", data.get("doc"), paramMap.get("doc")));
            List<Map<String, Object>> fields = (List<Map<String, Object>>) data.get("fields");
            List<String> indexKey = getIndexKey(name);
            for (Map<String, Object> field : fields) {
                if (indexKey.contains(field.get("name"))) {
                    Map<String, Object> ttlNull = Maps.newHashMap();
                    ttlNull.put("ttl", null);
                    field.put("doc", rewriteDoc("ttl", field.get("doc"), ttlNull));
                } else {
                    field.put("doc", rewriteDoc("ttl", field.get("doc"), JsonPath.read(paramMap, "$.fields[?(@.name == \"" + field.get("name") + "\")].doc")));
                }
                field.put("doc", rewriteDoc("visibility", field.get("doc"), JsonPath.read(paramMap, "$.fields[?(@.name == \"" + field.get("name") + "\")].doc")));
            }
            content = JSON.toJSONString(data, JSONWriter.Feature.WriteNulls);
            log.info("push Schema, content is: {}", content);
            return systemConfigService.publishConfig(name.concat(".json"), nacosConfig.getGroup(), JSONUtil.formatJsonStr(content));
        } else {
            throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(),QGWMessageConst.QUERY_SCHEMA_ERROR));
        }
    }

    /**
     * Desc: 重写[tables |field].doc
     * @param key
     * @param original
     * @param param
     * @return {@link Map< String, Object>}
     * @created by wWei
     * @date 2022/5/17 10:02 上午
     */
    private Map<String, Object> rewriteDoc(String key, Object original, Object param) {
        Map<String, Object> doc = Maps.newHashMap();
        if (StringUtil.isNotEmpty(original)) {
            doc = (Map<String, Object>) original;
        }
        Map<String, Object> paramDoc = Maps.newHashMap();
        if (param instanceof Map) {
            paramDoc = (Map<String, Object>) param;
        } else if (param instanceof List) {
            List item = (List) param;
            paramDoc = (Map<String, Object>) item.get(0);
        }
        if (paramDoc.containsKey(key)) {
            Object value = paramDoc.get(key);
            if (NumberUtil.isNumber(String.valueOf(value))) {
                doc.put(key, Long.parseLong(value.toString()));
            } else {
                doc.put(key, value);
            }
        } else {
            doc.remove(key);
        }
        return doc;
    }

    private void addMetadataListener() {
        try {
            systemConfigService.addListener(NacosConst.META_DATA_ID, nacosConfig.getGroup(), new AbstractListener() {
                @Override
                public void receiveConfigInfo(String configInfo) {
                    log.info("ReceiveConfigInfo metadata {}", configInfo);
                    schemaCache = Maps.newHashMap();
                }
            });
        } catch (NacosException e) {
            log.error("NacosException: ", e);
        }
    }

    private Map loadFields(String name, boolean displayStorageSize) {
        Schema schema = getSchemaByName(name);
        if (StringUtil.isEmpty(schema)) {
            throw new QGWBusinessException(HttpStatusCodeEnum.NOT_FOUND.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(), QGWMessageConst.SCHEMA_URL_NOT_FOUND);
        }
        LinkedHashMap resultMap = JSON.parseObject(String.valueOf(schema), LinkedHashMap.class);
        LinkedHashMap schemaDoc = JSON.parseObject(schema.getDoc(), LinkedHashMap.class);
        if (StringUtil.isEmpty(schemaDoc) &&
                StringUtil.isNotEmpty(schema.getDoc())) {
            log.error("{} schema's doc isn't jsonString and won't convert: {}", name, schema.getDoc());
        }
        List<Map> data = Lists.newArrayList();
        if (displayStorageSize) {
            data = getStorageSize(name);
            schemaDoc = (LinkedHashMap) fillSchemaStorageSize(schemaDoc, data);
        }
        if (StringUtil.isNotEmpty(schemaDoc)) {
            resultMap.put("doc", schemaDoc);
        }
        List<Map> fields = (List<Map>) resultMap.get("fields");
        LinkedHashMap mapDoc;
        for (Map next : fields) {
            mapDoc = JSON.parseObject((String) next.get("doc"), LinkedHashMap.class);
            mapDoc = (LinkedHashMap) fillFieldStorageSize(data, mapDoc, next);
            if (StringUtil.isNotEmpty(mapDoc)) {
                next.put("doc", mapDoc);
            }
            if (StringUtil.isEmpty(mapDoc) && !StringUtil.isEmpty(next.get("doc"))) {
                log.error("{} field's doc isn't jsonString and won't convert: {}", next.get("name"), next.get("doc"));
            }
        }
        fillReference(resultMap, name);
        return resultMap;
    }

    private Map fillSchemaStorageSize(LinkedHashMap schemaDoc, List<Map> data) {
        if (CollectionUtil.isEmpty(data)) {
            return schemaDoc;
        }
        if (StringUtil.isEmpty(schemaDoc)) {
            schemaDoc = new LinkedHashMap();
        }
        long total = 0;
        for (Map datum : data) {
            total += Long.parseLong(String.valueOf(datum.get("bytes")));
        }
        schemaDoc.put("size", total);
        return schemaDoc;
    }

    private Map fillFieldStorageSize(List<Map> data, LinkedHashMap mapDoc, Map next) {
        if (CollectionUtil.isEmpty(data)) {
            return mapDoc;
        }
        if (StringUtil.isEmpty(mapDoc)) {
            mapDoc = new LinkedHashMap();
        }
        try {
            List<Object> name = JsonPath.read(data, "$.[?(@.field == \"" + next.get("name") + "\")].bytes");
            mapDoc.put("size", Long.parseLong(String.valueOf(name.get(0))));
        } catch (RuntimeException e) {
            log.error("fill {} field storage size error, message is: {}", next.get("name"), e.getMessage());
        }
        return mapDoc;
    }

    private List<Map> getStorageSize(String name) {
        try {
            List<String> symbols = (List<String>) loadTables(clickHouseHttpSource.getDbName()).get("symbols");
            String sql = null;
            if (symbols.contains(name)) {
                sql = String.format(Objects.requireNonNull(environment.getProperty("SCHEMA_STORAGE_SIZE")), name, clickHouseHttpSource.getDbName());
            }
            BaseResult baseResult = StringUtil.isBlank(sql) ? BaseResultGenerator.success(Lists.newArrayList()) : sqlSyncQueryService.executeQuery(SQLQueryContext.builder().rawSQL(sql).build());
            if (baseResult.isSuccess()) {
                return (List<Map>) baseResult.getData();
            } else {
                log.error("get {} schema storage size error on DB.", name);
            }
        } catch (RuntimeException ex) {
            log.error("get {} schema storage size error on DB, message is {}", name, ex.getMessage());
        }
        return Lists.newArrayList();
    }

    /**
     * @param map       需操作对象
     * @param cfgName   当前文件名称
     * @return void
     * @Description     $ref实际引用部分赋值
     * @author wanghao
     * @date 2021/9/1 17:02
     */

    private void fillReference(Map map, String cfgName) {
        for (Object mapKey : map.keySet()) {
            Object keyObj = map.get(mapKey);
            if (keyObj instanceof Map) {
                Map mapValue = (Map) keyObj;
                if (mapValue.containsKey(KEY_REF)) {
                    if (INDEX_KEY.equals(String.valueOf(mapKey))) {
                        String s = String.valueOf(mapValue.get(KEY_REF));
                        mapValue.put(KEY_REF, s.contains("public_schema_info_2311.json") ? s.concat("/" + schemaVersion) : s);
                    }
                    Object refValue = getRefValue(cfgName, mapValue);
                    map.put(mapKey, regexValue(refValue, mapValue));
                } else {
                    fillReference(mapValue, cfgName);
                }
            } else if (keyObj instanceof List) {
                List listValue = (List) keyObj;
                for (Object next : listValue) {
                    if (next instanceof Map) {
                        fillReference((Map) next, cfgName);
                    }
                }
            }
        }
    }

    @Override
    public Object getCfg(String cfgName) {
        if (originalCache.containsKey(cfgName)) {
            return originalCache.get(cfgName);
        }
        String content = null;
        try {
            content = getPubCfg(cfgName);
            if (!StrUtil.isBlankIfStr(content)) {
                originalCache.put(cfgName, content);
                return content;
            }
            content = systemConfigService.getConfig(cfgName, nacosConfig.getGroup(), 3000);
            originalCache.put(cfgName, content);
            systemConfigService.addListener(cfgName, nacosConfig.getGroup(), new AbstractListener() {
                @Override
                public void receiveConfigInfo(String configInfo) {
                    log.info("ReceiveConfigInfo Schema Fields {}", configInfo);
                    originalCache.put(cfgName, configInfo);
                }
            });
        } catch (NacosException e) {
            log.error("NacosException:{}", e);
        }
        return content;
    }

    private String getPubCfg(String cfgName) throws NacosException {
        String content = pubConfigService.getConfig(cfgName, NacosConst.DEFAULT_GROUP, 3000);
        pubConfigService.addListener(cfgName, NacosConst.DEFAULT_GROUP, new AbstractListener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
                log.info("ReceiveConfigInfo Schema Fields {}", configInfo);
                originalCache.put(cfgName, configInfo);
            }
        });
        return content;
    }

    private Object getRefValue(String schemaName, Map map) {
        Object data = null;
        String[] split = getRefStrArray(map);
        String jsonPath = getJsonPath(split);
        try {
            Object cfg = getCfg(getFileName(split, schemaName));
            data = JsonPath.read(String.valueOf(cfg), jsonPath);
        } catch (RuntimeException e) {
            log.warn("read reference schema error: {}", e);
        }
        return data;
    }

    private Object regexValue(Object obj, Map<String, String> map) {
        if (map.size() <= 1) {
            return obj;
        }
        List<Object> key = Lists.newArrayList();
        List<Object> value = Lists.newArrayList();
        try {
            key.addAll(JsonPath.read(obj, map.get(KEY)));
            value.addAll(JsonPath.read(obj, map.get(VALUE)));
        } catch (RuntimeException ex) {
            log.warn("parse reference schema error: JsonPath.read error");
            return Lists.newArrayList();
        }
        if (key.size() != value.size()) {
            log.warn("parse reference schema error: key and value non-correspondence");
            return Lists.newArrayList();
        }
        List<Map<String, Object>> list = Lists.newArrayList();
        for (int i = 0; i < key.size(); i++) {
            HashMap<String, Object> item = Maps.newLinkedHashMap();
            item.put(CODE, key.get(i));
            item.put(VALUE, value.get(i));
            list.add(item);
        }
        return list;
    }

    private String[] getRefStrArray(Map refMap) {
        String v = (String) refMap.get(KEY_REF);
        return v.split("#", 2);
    }

    private String getJsonPath(String[] strArray) {
        return "$".concat(strArray[1].replace("/", "."));
    }

    private String getFileName(String[] strArray, String fileName) {
        return StrUtil.isEmpty(strArray[0]) ? fileName.concat(NacosConst.JSON_SUFFIX) : strArray[0];
    }

    /**
     * @param name
     * @Description: 从Nacos配置中心获取Tables
     * @Author: liuyongqiang
     * @Date: 2021/3/11 18:17
     * @return: com.mesalab.common.base.BaseResult
     **/
    private Map<String, Object> loadTables(String name) {
        Map<String, Object> date = new LinkedHashMap<>();
        List<String> tables = new ArrayList<>();
        metadataConfig.getMetadata().forEach(o -> {
                    if (o.getNamespace().equalsIgnoreCase(name)) {
                        tables.addAll(o.getTables());
                    }
                }
        );
        if (tables.isEmpty()) {
            throw new QGWBusinessException(HttpStatusCodeEnum.NOT_FOUND.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(), QGWMessageConst.SCHEMA_URL_NOT_FOUND);
        }
        date.put("type", "enum");
        date.put("name", name);
        date.put("symbols", tables);
        return date;
    }

    private Schema parseSchema(String configInfo) {
        if (StrUtil.isBlank(configInfo)) return null;
        Map schemaMap = null;
        try {
            schemaMap = JSON.parseObject(configInfo, Map.class);
            Object docSchema;
            if (StringUtil.isNotEmpty(docSchema = schemaMap.get("doc"))) {
                schemaMap.put("doc", JSON.toJSONString(docSchema, JSONWriter.Feature.WriteNulls));
            }
            Object fieldsObj;
            if (StringUtil.isNotEmpty(fieldsObj = schemaMap.get("fields"))) {
                List<Map> fields = (List<Map>) fieldsObj;
                for (Map next : fields) {
                    Object docField;
                    if (StringUtil.isEmpty(docField = next.get("doc"))) {
                        continue;
                    }
                    next.put("doc", JSON.toJSONString(docField, JSONWriter.Feature.WriteNulls));
                }
            }
        } catch (JSONException | SchemaParseException | ClassCastException e) {
            log.error("update Schema error: {}", e);
        }
       return new Schema.Parser().parse(JSON.toJSONString(schemaMap, JSONWriter.Feature.WriteNulls));
    }

    private BaseResult resetBaseResult(BaseResult result, String alias) {
        if (!result.isSuccess()) {
            return result;
        }
        List<Map> data = (List<Map>) result.getData();
        for (Map map : data){
            String logType = LogType.getLabelByValue(String.valueOf(map.get(alias)));
            BaseResult deleteStorageStatus = getDeleteStorageStatus(String.valueOf(map.get(alias)));
            List<StorageDeletionInfo> data1 = (List<StorageDeletionInfo>) deleteStorageStatus.getData();
            map.put(alias, logType);
            map.put("max_days",data1.get(0).getMaxDays());
            map.put("default_max_days",data1.get(0).getDefaultMaxDays());
        }
        return result;
    }

    private boolean logTypeValid(List<StorageDeletionInfo> list) {
        for (StorageDeletionInfo info : list) {
            String value = DatabaseServiceImpl.LogType.getValueByLabel(info.getType());
            if (StringUtil.isEmpty(value)) {
                return false;
            }
        }
        return true;
    }

    /**
     * @Description 涉及对ALL、LogType(value，label)处理
     * @Param list:
     * @return: java.util.List<com.mesalab.qgw.model.job.StorageDeletionInfo>
     * @Created by wWei
     */
    private List<StorageDeletionInfo> preProcessOfLogType(List<StorageDeletionInfo> list) {
        if (list.size() == 1){
            StorageDeletionInfo deletionInfo = list.get(0);
            Integer maxDays = deletionInfo.getMaxDays();
            Integer defaultMaxDays = deletionInfo.getDefaultMaxDays();
            if (LogType.ALL.getValue().equalsIgnoreCase(list.get(0).getType())){
                list.clear();
                list.add(new StorageDeletionInfo(LogType.TRAFFIC_LOGS.getValue(), maxDays, defaultMaxDays));
                list.add(new StorageDeletionInfo(LogType.REPORT_AND_METRICS.getValue(), maxDays, defaultMaxDays));
                list.add(new StorageDeletionInfo(LogType.FILES.getValue(), maxDays, defaultMaxDays));
                return list;
            }else if (LogType.TRAFFIC_LOGS.getValue().equalsIgnoreCase(list.get(0).getType()) |
                    LogType.FILES.getValue().equalsIgnoreCase(list.get(0).getType()) |
                    LogType.REPORT_AND_METRICS.getLabel().equalsIgnoreCase(list.get(0).getType())) {
                list.clear();
                list.add(new StorageDeletionInfo(LogType.getValueByLabel(deletionInfo.getType()), maxDays, defaultMaxDays));
                return list;
            }
        }
        list.forEach(o -> o.setType(LogType.getValueByLabel(o.getType())));
        return list;
    }

    private boolean jobIsBusy(List<StorageDeletionInfo> list) {
        setCookie();
        for (StorageDeletionInfo info : list) {

            String handler = getDeletePartHandlerByLogType(info.getType());
            if (jobIsBusyByHandler(handler)) {
                return true;
            }
            handler = getDeleteAllHandlerByLogType(info.getType());
            if (jobIsBusyByHandler(handler)) {
                return true;
            }
        }
        return false;
    }

    private String getDeletePartHandlerByLogType(String logType) {
        String jobHandler = StringUtil.EMPTY;
        if (LogType.TRAFFIC_LOGS.getValue().equalsIgnoreCase(logType)) {
            jobHandler = JobHandlerOption.DELETE_TRAFFIC_DATA_JOB_HANDLER.getValue();
        } else if (LogType.REPORT_AND_METRICS.getValue().equalsIgnoreCase(logType)) {
            jobHandler = JobHandlerOption.DELETE_REPORT_AND_METRICS_DATA_JOB_HANDLER.getValue();
        } else if (LogType.FILES.getValue().equalsIgnoreCase(logType)) {
            jobHandler = JobHandlerOption.DELETE_FILES_JOB_HANDLER.getValue();
        }
        return jobHandler;
    }

    private boolean jobIsBusyByHandler(String handler) {
        Map dataByHandler = getDataByHandler(handler);
        String id = String.valueOf(dataByHandler.get("id"));
        BaseResult result = queryJobStatusByJobId(Integer.parseInt(id));
        if (result.getStatus().equals(HttpStatusCodeEnum.LOCKED.getCode())) {
            return true;
        } else if (!result.getStatus().equals(HttpStatusCodeEnum.SUCCESS.getCode())) {
            throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(),result.getMessage()));
        }
        return false;
    }

    private void updateSchema(StorageDeletionInfo info, Map dataByHandler) {
        Map<String, Map> previous = Maps.newHashMap();
        try {
            Map schemaInfo = getSchemaInfo(MetadataType.TABLES.getValue(), clickHouseHttpSource.getDbName(), false);
            Object tables = schemaInfo.get("symbols");
            if (StringUtil.isEmpty(tables)) {
                return;
            }
            long ttl = 24 * 60 * 60 * info.getMaxDays();
            List<String> list = (List) tables;
            for (String tableName : list) {
                Map schemaMap = getSchemaInfo(MetadataType.FIELDS.getValue(), tableName, false);
                List<Object> schemaDocTTL = JsonPath.read(schemaMap, "$.[?(@.doc.ttl != null)].doc.ttl");
                List<Object> fieldDocTTL = JsonPath.read(schemaMap, "$.fields[?(@.doc.ttl != null)].doc.ttl");
                if (schemaDocTTL.isEmpty() && fieldDocTTL.isEmpty()) {
                    continue;
                }
                previous.put(tableName, getSchemaInfo(MetadataType.FIELDS.getValue(), tableName, false));
                Object schemaDoc = schemaMap.get("doc");
                Map<String, Object> map = Maps.newHashMap();
                if (StringUtil.isNotEmpty(schemaDoc)) {
                    map = (Map<String, Object>) schemaDoc;
                }
                if (StringUtil.isNotEmpty(map.get("ttl")) && ttl < Long.parseLong(map.get("ttl").toString())) {
                    map.put("ttl", ttl);
                    schemaMap.put("doc", map);
                }
                List<Map<String, Object>> fields = JsonPath.read(schemaMap, "$.fields");
                List<String> indexKey = getIndexKey(tableName);
                for (Map<String, Object> field : fields) {
                    Object doc = field.get("doc");
                    if (StringUtil.isEmpty(doc)) {
                        doc = Maps.newHashMap();
                    }
                    Map<String, Object> fieldDoc = (Map<String, Object>) doc;
                    if (indexKey.contains(field.get("name").toString())) {
                        fieldDoc.put("ttl", null);
                        field.put("doc", fieldDoc);
                        continue;
                    }
                    if (StringUtil.isNotEmpty(fieldDoc.get("ttl")) && ttl < Long.parseLong(fieldDoc.get("ttl").toString())) {
                        fieldDoc.put("ttl", ttl);
                        field.put("doc", fieldDoc);
                    }
                }
                updateSchema(tableName, schemaMap);
            }
        } catch (RuntimeException ex) {
            for (String tableName : previous.keySet()) {
                updateSchema(tableName, previous.get(tableName));
            }
            executeManageJob("update", mapToBean(dataByHandler, XxlJobInfo.class));
            throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(),
                    CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(),"up schema error in storage set ", ex.getMessage()));
        }
    }


    /**
     * 通过handler Value获取jobInfo数据
     *
     * @param handlerValue
     * @return
     */
    private Map getDataByHandler(String handlerValue) {
        StringBuilder url = new StringBuilder(jobAdminHttpSource.getUrl()).
                append("/jobinfo/pageList?jobGroup=-1&triggerStatus=-1&executorHandler=").
                append(handlerValue);
        Map<String, String> resultPageList = httpClientService.httpGet(url.toString(), headers, httpConfig.getServerResponseTimeOut());
        if (StringUtil.isNotEmpty(resultPageList) && resultPageList.get("status").equals(String.valueOf(HttpStatusCodeEnum.SUCCESS.getCode()))) {
            Map<String, Object> maps = JSON.parseObject(resultPageList.get("result"), Map.class);
            if (StringUtil.isEmpty(maps)) {
                throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                        String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(),"Get or package result exception: " + JSON.toJSONString(resultPageList)));
            }
            List<Map> data = (List) maps.get("data");
            if (data.size() >= 1) {
                return data.get(0);
            } else {
                throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                        String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(),"The scheduled task has no task information matching executorHandler (" + handlerValue + ")"));
            }
        }
        throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(),"Get scheduled task exception: " + JSON.toJSONString(resultPageList)));
    }

    /**
     * 查询任务状态
     *
     * @param jobId
     * @return
     */
    private BaseResult queryJobStatusByJobId(int jobId) {
        BaseResult baseResult = null;
        StringBuilder url = new StringBuilder(jobAdminHttpSource.getUrl()).append("/jobinfo/jobBeat?jobId=").append(jobId);
        Map<String, String> resultMap = httpClientService.httpGet(url.toString(), headers, httpConfig.getServerResponseTimeOut());
        baseResult = resultEncapsulationOfJob(resultMap);
        return baseResult;
    }

    /**
     * 执行调度任务结果封装
     *
     * @param resultMap
     * @return
     */
    private BaseResult resultEncapsulationOfJob(Map<String, String> resultMap) {
        BaseResult baseResult;
        if (StringUtil.isEmpty(resultMap)) {
            baseResult = BaseResultGenerator.failure(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(),"The service is busy, please contact the scheduling platform!"));
        } else {
            if (resultMap.get("status").equals(String.valueOf(HttpStatusCodeEnum.SUCCESS.getCode()))) {
                Map result = JSON.parseObject(resultMap.get("result"), Map.class);
                if (result.get("code").equals(HttpStatusCodeEnum.SUCCESS.getCode())) {
                    baseResult = BaseResultGenerator.success("ok", null);
                } else if (result.get("code").equals(HttpStatusCodeEnum.LOCKED.getCode())) {
                    baseResult = BaseResultGenerator.failure(HttpStatusCodeEnum.LOCKED.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                            String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(), "Clear Task is Running."));
                } else {
                    baseResult = BaseResultGenerator.failure(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                            String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(), result.get("msg")));
                }
            } else {
                baseResult = BaseResultGenerator.failure(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                        String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(), "Clear Task Failed."));
            }
        }
        return baseResult;
    }

    private String getDeleteAllHandlerByLogType(String logType) {
        String jobHandler = StringUtil.EMPTY;
        if (LogType.TRAFFIC_LOGS.getValue().equalsIgnoreCase(logType)) {
            jobHandler = JobHandlerOption.DELETE_ALL_TRAFFIC_DATA_JOB_HANDLER.getValue();
        } else if (LogType.REPORT_AND_METRICS.getValue().equalsIgnoreCase(logType)) {
            jobHandler = JobHandlerOption.DELETE_ALL_REPORT_AND_METRICS_DATA_JOB_HANDLER.getValue();
        } else if (LogType.FILES.getValue().equalsIgnoreCase(logType)) {
            jobHandler = JobHandlerOption.DELETE_ALL_FILES_JOB_HANDLER.getValue();
        }
        return jobHandler;
    }

    /**
     * 获取调度平台Cookie
     *
     * @return
     */
    public void setCookie() {
        StringBuilder url = new StringBuilder(jobAdminHttpSource.getUrl());
        headers.put("Content-Type", "application/json");
        String urlParamsByMap = getUrlParamsByMap(getObjectToMap(jobAdminHttpSource));
        int socketTimeOut = httpConfig.getServerResponseTimeOut();
        Map httpPostResponseHeads = httpClientService.getHttpPostResponseHeads(url + "/login?" + urlParamsByMap, headers, socketTimeOut);
        String cookie = String.valueOf(httpPostResponseHeads.get("SET-COOKIE"));
        headers.put("Cookie", cookie);
    }

    public static String getUrlParamsByMap(Map<String, Object> map) {
        if (map == null) {
            return "";
        }
        StringBuffer sb = new StringBuffer();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            sb.append(entry.getKey() + "=" + Encodes.urlEncode(String.valueOf(entry.getValue())));
            sb.append("&");
        }
        String s = sb.toString();
        if (s.endsWith("&")) {
            s = StringUtil.substringBeforeLast(s, "&");
        }
        return s;
    }

    public static Map<String, Object> getObjectToMap(Object obj) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        Class<?> clazz = obj.getClass();
        Field[] declaredFields = clazz.getDeclaredFields();
        AccessibleObject.setAccessible(declaredFields, true);
        for (Field field : declaredFields) {
            String fieldName = field.getName();
            Object value = null;
            try {
                value = field.get(obj);
            } catch (IllegalAccessException e) {
                log.error("Illegal Access Exception: ", e);
            }
            if (value == null) {
                value = "";
            }
            map.put(fieldName, value);
        }
        return map;
    }

    /**
     * 执行日志删除任务: 调用调度任务任务
     *
     * @param info
     * @return
     */
    private BaseResult executeDeleteStorageJob(StorageDeletionInfo info) {

        BaseResult baseResult;
        String jobHandler;
        String logType = info.getType();
        ExecutorParam executorParam = new ExecutorParam();
        if (0 == info.getMaxDays()) {
            jobHandler = getDeleteAllHandlerByLogType(logType);
            Map dataByHandler = getDataByHandler(jobHandler);

            StorageDeletionInfo storageDeletionInfoByHandler = getStorageDeletionInfoByHandler(logType, getDeletePartHandlerByLogType(logType));
            executorParam.setMaxDays(storageDeletionInfoByHandler.getMaxDays());
            XxlJobInfo xxlJobInfo = setXxlJobInfoParam(dataByHandler, executorParam);
            baseResult = executeTriggerAndUpdate(xxlJobInfo);
        } else {
            jobHandler = getDeletePartHandlerByLogType(info.getType());
            executorParam.setMaxDays(info.getMaxDays());
            Map oldData = getDataByHandler(jobHandler);
            XxlJobInfo newData = setXxlJobInfoParam(oldData, executorParam);
            baseResult = executeManageJob("update", newData);
            if (baseResult.isSuccess() && LogType.TRAFFIC_LOGS.getValue().equalsIgnoreCase(info.getType())) {
                updateSchema(info, oldData);
            }
            if (baseResult.isSuccess()) {
                baseResult = executeManageJob("trigger", newData);
            }
        }
        if (!baseResult.getStatus().equals(HttpStatusCodeEnum.SUCCESS.getCode())) {
            throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(),QGWMessageConst.SCHEDULED_TASK_ERROR));
        }
        return baseResult;
    }

    /**
     * 触发执行器并更新任务
     *
     * @param xxlJobInfo
     * @return
     */
    private BaseResult executeTriggerAndUpdate(XxlJobInfo xxlJobInfo) {
        BaseResult baseResult;
        BaseResult resultExecute = executeManageJob("trigger", xxlJobInfo);
        if (resultExecute.getStatus().equals(HttpStatusCodeEnum.SUCCESS.getCode())) {
            BaseResult resultUpdate = executeManageJob("update", xxlJobInfo);
            if (resultUpdate.getStatus().equals(HttpStatusCodeEnum.SUCCESS.getCode())) {
                baseResult = BaseResultGenerator.success("ok", null);
            } else {
                baseResult = BaseResultGenerator.failure(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                        String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(),"Update task failed:" + xxlJobInfo.getExecutorHandler()));
            }
        } else {
            baseResult = BaseResultGenerator.failure(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(),"Failed to execute task:" + xxlJobInfo.getExecutorHandler()));
        }
        return baseResult;
    }

    private StorageDeletionInfo getStorageDeletionInfoByHandler(String logType, String jobHandlerValue) {
        Map trafficDate = getDataByHandler(jobHandlerValue);
        XxlJobInfo xxlJobInfo = mapToBean(trafficDate, XxlJobInfo.class);
        StorageDeletionInfo executorParam = JSON.parseObject(StrUtil.toUnderlineCase(xxlJobInfo.getExecutorParam()), StorageDeletionInfo.class);
        executorParam.setType(logType);
        return executorParam;
    }

    public static <T> T mapToBean(Map<String, Object> map, Class<T> clazz) {
        T bean = null;
        try {
            bean = clazz.newInstance();
        } catch (InstantiationException e) {
            log.error("Instantiation Exception: ", e);
        } catch (IllegalAccessException e) {
            log.error("Illegal Access Exception: ", e);
        }
        BeanMap beanMap = BeanMap.create(bean);
        beanMap.putAll(map);
        return bean;
    }

    private XxlJobInfo setXxlJobInfoParam(Map data, ExecutorParam executorParam) {
        XxlJobInfo xxlJobInfo = mapToBean(data, XxlJobInfo.class);
        ExecutorParam executor = JSON.parseObject(xxlJobInfo.getExecutorParam(), ExecutorParam.class);
        executorParam.setDefaultMaxDays(executor.getDefaultMaxDays());
        xxlJobInfo.setExecutorParam(JSON.toJSONString(executorParam));
        return xxlJobInfo;
    }

    /**
     * 执行调度平台任务更新
     *
     * @param executeType
     * @param xxlJobInfo
     * @return
     */
    private BaseResult executeManageJob(String executeType, XxlJobInfo xxlJobInfo) {
        BaseResult baseResult = null;
        String params = getUrlParamsByMap(getObjectToMap(xxlJobInfo));
        String url = String.format("%s/jobinfo/%s/?%s", jobAdminHttpSource.getUrl(), executeType, params);
        Map<String, String> resultMap = httpClientService.httpGet(url, headers, httpConfig.getServerResponseTimeOut());
        log.warn("请求调度任务" + executeType + "接口" + url);
        baseResult = resultEncapsulationOfJob(resultMap);
        return baseResult;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.env = environment;
    }

    @Getter
    @AllArgsConstructor
    enum LogType {
        TRAFFIC_LOGS("Traffic Logs", "Traffic Logs"),
        REPORT_AND_METRICS("Report and Metrics", "Metrics"),
        FILES("Files", "Files"),
        ALL("All", "All");
        private final String value;
        private final String label;


        public static String getValueByLabel(String label) {
            for (LogType enums : LogType.values()) {
                if (enums.getLabel().equals(label)) {
                    return enums.getValue();
                }
            }
            return "";
        }

        public static String getLabelByValue(String value) {
            for (LogType enums : LogType.values()) {
                if (enums.getValue().equals(value)) {
                    return enums.getLabel();
                }
            }
            return "";
        }
    }
}
