package com.mesalab.qgw.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
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
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayway.jsonpath.JsonPath;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.common.enums.DBTypeEnum;
import com.mesalab.common.enums.MetadataTypeEnum;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.nacos.NacosConfig;
import com.mesalab.common.nacos.NacosConst;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.qgw.model.basic.QueryProfile;
import com.mesalab.qgw.model.basic.ClickHouseHttpSource;
import com.mesalab.qgw.model.metadata.MetadataBean;
import com.mesalab.qgw.model.metadata.MetadataConfig;
import com.mesalab.qgw.service.QueryService;
import com.mesalab.qgw.service.MetadataService;
import com.zdjizhi.utils.StringUtil;
import org.apache.avro.Schema;
import org.apache.avro.SchemaParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;

/**
 * @Date: 2021-03-11 15:48
 * @Author : liuyongqiang
 * @ClassName : NacosMetadataServiceImpl
 * @Description : 获取Nacos注册中心元数据实现类
 */
@Service("metadataService")
public class MetadataServiceImpl implements MetadataService {

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
    private QueryService queryService;
    @Autowired
    public MetadataConfig metadataConfig;
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

    @PostConstruct
    public void init() {
        addMetadataListener();
        log.info("Initializing Schema Tables Complete");
    }

    @Override
    public Map<String, Object> getSchemaInfo(String type, String name, boolean displayStorageSize) {
        if (MetadataTypeEnum.TABLES.getValue().equals(type)) {
            return loadTables(name);
        } else if (MetadataTypeEnum.FIELDS.getValue().equals(type)) {
            return loadFields(name, displayStorageSize);
        }
        throw new QGWBusinessException(ResultStatusEnum.NOT_FOUND.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), QGWMessageConst.SCHEMA_URL_NOT_FOUND));
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
    public String getDBTypeByTableName(String tableName) {
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
                type = DBTypeEnum.CLICKHOUSE.getValue();
                break;
            case "ELASTICSEARCH_GROUP":
                type = DBTypeEnum.ELASTICSEARCH.getValue();
                break;
            case "HBASE_GROUP":
                type = DBTypeEnum.HBASE.getValue();
                break;
            case "DRUID_GROUP":
                type = DBTypeEnum.DRUID.getValue();
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
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(),
                    ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),
                    tableName + ":"+QGWMessageConst.TABLE_NOT_EXIST));
        }
        Schema schema;
        if (!StrUtil.isBlankIfStr(schema = schemaCache.get(tableName))) {
            return schema;
        }
        try {
            String content = systemConfigService.getConfig(tableName.concat(NacosConst.JSON_SUFFIX), nacosConfig.getGroup(), 3000);
            if (StrUtil.isBlankIfStr(content)) {
                throw new QGWBusinessException(ResultStatusEnum.SERVER_ERROR.getCode(),
                        ResultCodeEnum.PARAMETER_ERROR.getCode(),
                        String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),tableName + ":"+QGWMessageConst.QUERY_SCHEMA_ERROR));
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
            throw new QGWBusinessException(ResultStatusEnum.SERVER_ERROR.getCode(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(),
                    e.getMessage());
        }
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
            throw new QGWBusinessException(ResultStatusEnum.SERVER_ERROR.getCode(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(),
                    String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(),QGWMessageConst.QUERY_SCHEMA_ERROR));
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
            throw new QGWBusinessException(ResultStatusEnum.NOT_FOUND.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(), QGWMessageConst.SCHEMA_URL_NOT_FOUND);
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
            BaseResult baseResult = StringUtil.isBlank(sql) ? BaseResultGenerator.success(Lists.newArrayList()) : queryService.executeQuery(QueryProfile.builder().query(sql).build());
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
                        mapValue.put(KEY_REF, String.valueOf(mapValue.get(KEY_REF)).concat("/" + schemaVersion));
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
            throw new QGWBusinessException(ResultStatusEnum.NOT_FOUND.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(), QGWMessageConst.SCHEMA_URL_NOT_FOUND);
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
}
