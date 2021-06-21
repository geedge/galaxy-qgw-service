package com.mesalab.qgw.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONException;
import cn.hutool.json.JSONUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.DBTypeEnum;
import com.mesalab.common.enums.MetadataTypeEnum;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.nacos.NacosConfig;
import com.mesalab.common.nacos.NacosConst;
import com.mesalab.qgw.model.api.ClickHouseHttpSource;
import com.mesalab.qgw.model.api.DruidIoHttpSource;
import com.mesalab.qgw.model.api.ElasticsearchHttpSource;
import com.mesalab.qgw.model.metadata.MetadataBean;
import com.mesalab.qgw.service.MetadataService;
import com.zdjizhi.utils.JsonMapper;
import com.zdjizhi.utils.StringUtil;
import org.apache.avro.Schema;
import org.apache.avro.SchemaParseException;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.Executor;

import static java.util.stream.Collectors.toList;

/**
 * @Date: 2021-03-11 15:48
 * @Author : liuyongqiang
 * @ClassName : NacosMetadataServiceImpl
 * @Description : 获取Nacos注册中心元数据实现类
 */
@Service("nacosMetadataService")
public class NacosMetadataServiceImpl implements MetadataService {

    private static final Log log = LogFactory.get();
    @Autowired
    private NacosConfig nacosConfig;
    @Autowired
    private ConfigService configService;
    @Autowired
    private ClickHouseHttpSource clickHouseHttpSource;
    @Autowired
    private DruidIoHttpSource druidIoHttpSource;
    @Autowired
    private ElasticsearchHttpSource elasticsearchHttpSource;

    public List<MetadataBean> metadataBeans;

    public Map<String, Schema> allSchemaMap = new HashMap<>();

    @Override
    public BaseResult getSchemaInfo(String type, String name) {
        if (MetadataTypeEnum.TABLES.getValue().equals(type)) {
            return loadTables(name);
        } else if (MetadataTypeEnum.FIELDS.getValue().equals(type)) {
            return loadFields(name);
        } else {
            return BaseResultGenerator.failure(HttpStatus.SC_NOT_FOUND,
                    ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                    "Not found, check url please");
        }
    }

    @Override
    public String getPartitionKey(String tableName) {
        String partitionKey = StringUtil.EMPTY;
        Schema schema = getSchemaByName(tableName);
        Map doc = (Map) JsonMapper.fromJsonString(schema.getDoc(), Map.class);
        if (StringUtil.isNotEmpty(doc)) {
            Object partition_key = doc.get("partition_key");
            partitionKey = StringUtil.isEmpty(partition_key) ? StringUtil.EMPTY : partition_key.toString();
        }
        return partitionKey;
    }

    @Override
    public String getValueByKeyInSchemaDoc(String tableName, String key) {
        Schema schema = getSchemaByName(tableName);
        Map doc = (Map) JsonMapper.fromJsonString(schema.getDoc(), Map.class);
        if (StringUtil.isNotEmpty(doc)) {
            Object value = doc.get(key);
            if (value instanceof Map) {
                return JsonMapper.toJsonString(value);
            } else {
                return StringUtil.isEmpty(value) ? StringUtil.EMPTY : value.toString();
            }
        }
        return StringUtil.EMPTY;
    }

    @Override
    public String getDBTypeByTableName(String tableName) {
        Schema schema = getSchemaByName(tableName);
        if (StringUtil.isEmpty(schema)) {
            return StringUtil.EMPTY;
        }
        if (clickHouseHttpSource.getDbName().equalsIgnoreCase(schema.getNamespace())
                || "system".equalsIgnoreCase(schema.getNamespace())) {
            return DBTypeEnum.CLICKHOUSE.getValue();
        }
        if (druidIoHttpSource.getDbname().equalsIgnoreCase(schema.getNamespace())) {
            return DBTypeEnum.DRUID.getValue();
        }
        if (elasticsearchHttpSource.getDbname().equalsIgnoreCase(schema.getNamespace())) {
            return DBTypeEnum.ELASTICSEARCH.getValue();
        }
        return StringUtil.EMPTY;
    }

    @Override
    public String getDBNameByTableName(String tableName) {
        Schema schema = getSchemaByName(tableName);
        return StringUtil.isNotEmpty(schema) ? schema.getNamespace() : clickHouseHttpSource.getDbName();
    }

    @Override
    public Map<String, Schema> getAllSchema() {
        return allSchemaMap;
    }

    @PostConstruct
    public void init() {
        initTables();
        log.info("Initializing Schema Tables Complete");
        initFields();
        log.info("Initializing Schema Fields Complete");
    }

    private void initTables() {
        try {
            String content = configService.getConfig(NacosConst.META_DATA_ID, nacosConfig.getGroup(), 3000);
            metadataBeans = JSONUtil.toList(content, MetadataBean.class);
            configService.addListener(NacosConst.META_DATA_ID, nacosConfig.getGroup(), new Listener() {
                @Override
                public Executor getExecutor() {
                    return null;
                }

                @Override
                public void receiveConfigInfo(String configInfo) {
                    log.info("ReceiveConfigInfo Schema Tables {}", configInfo);
                    updateMetaData(configInfo);
                }
            });
        } catch (NacosException e) {
            log.error("NacosException: ", e);
        }
    }

    private void updateMetaData(String configInfo) {
        List<MetadataBean> tempMetadatabeans = JSONUtil.toList(configInfo, MetadataBean.class);
        filterTables(tempMetadatabeans, NacosConst.CLICKHOUSE_GROUP);
        filterTables(tempMetadatabeans, NacosConst.DRUID_GROUP);
        filterTables(tempMetadatabeans, NacosConst.ES_GROUP);
        metadataBeans = tempMetadatabeans;
    }

    private void filterTables(List<MetadataBean> tempMetadatabeans, String group) {
        List<String> tempTables = tempMetadatabeans.stream().filter(item -> item.getGroup().equals(group)).findFirst().get().getTables();
        List<String> tables = metadataBeans.stream().filter(item -> item.getGroup().equals(group)).findFirst().get().getTables();
        List<String> reduce;
        if (tempTables.size() > tables.size()) {
            reduce = tempTables.stream().filter(item -> !tables.contains(item)).collect(toList());
            addListener(reduce);
        } else if (tempTables.size() < tables.size()) {
            reduce = tables.stream().filter(item -> !tempTables.contains(item)).collect(toList());
            for (String s : reduce) {
                allSchemaMap.remove(s);
            }
        }
    }

    private void addListener(List<String> reduce) {
        for (String s : reduce) {
            try {
                configService.addListener(s, nacosConfig.getGroup(), new Listener() {
                    @Override
                    public Executor getExecutor() {
                        return null;
                    }

                    @Override
                    public void receiveConfigInfo(String configInfo) {
                        log.info("ReceiveConfigInfo Schema Tables {}", configInfo);
                        updateSchema(configInfo);
                    }
                });
            } catch (NacosException e) {
                log.error("NacosException: ", e);
            }
        }
    }

    private void initFields() {
        for (MetadataBean metadataBean : metadataBeans) {
            List<String> tables = metadataBean.getTables();
            for (String table : tables) {
                try {
                    String content = configService.getConfig(table.concat(NacosConst.JSON_SUFFIX), nacosConfig.getGroup(), 3000);
                    updateSchema(content);
                    configService.addListener(table.concat(NacosConst.JSON_SUFFIX), nacosConfig.getGroup(), new Listener() {
                        @Override
                        public Executor getExecutor() {
                            return null;
                        }

                        @Override
                        public void receiveConfigInfo(String configInfo) {
                            log.info("ReceiveConfigInfo Schema Fields {}", configInfo);
                            updateSchema(configInfo);
                        }
                    });
                } catch (NacosException e) {
                    log.error("NacosException:{}", e);
                }
            }
        }
    }

    private BaseResult loadFields(String name) {
        Schema schema = getSchemaByName(name);
        if (StringUtil.isEmpty(schema)) {
            return BaseResultGenerator.failure(HttpStatus.SC_NOT_FOUND,
                    ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                    "Not found, check url please");
        }
        Map resultMap = (Map) JsonMapper.fromJsonString(String.valueOf(schema), Map.class);
        Map schemaDoc = (Map) JsonMapper.fromJsonString(schema.getDoc(), Map.class);
        if (StringUtil.isEmpty(schemaDoc) &&
                StringUtil.isNotEmpty(schema.getDoc())) {
            log.error("{} schema's doc isn't jsonString and won't convert: {}", name, schema.getDoc());
        }
        if (StringUtil.isNotEmpty(schemaDoc)) {
            resultMap.put("doc", schemaDoc);
        }
        List<Map> fields = (List<Map>) resultMap.get("fields");
        Map mapDoc;
        for (Map next : fields) {
            mapDoc = (Map) JsonMapper.fromJsonString(String.valueOf(next.get("doc")), Map.class);
            if (StringUtil.isNotEmpty(mapDoc)) {
                next.put("doc", mapDoc);
            }
            if (StringUtil.isEmpty(mapDoc) && !StringUtil.isEmpty(next.get("doc"))) {
                log.error("{} field's doc isn't jsonString and won't convert: {}", next.get("name"), next.get("doc"));
            }
        }
        return BaseResultGenerator.success("ok", resultMap);
    }

    /**
     * @param name
     * @Description: 从Nacos配置中心获取Tables
     * @Author: liuyongqiang
     * @Date: 2021/3/11 18:17
     * @return: com.mesalab.common.base.BaseResult
     **/
    private BaseResult loadTables(String name) {
        Map<String, Object> date = new LinkedHashMap<>();
        List<String> tables = new ArrayList<>();
        boolean existName = false;
        for (MetadataBean metadataBean : metadataBeans) {
            String namespace = metadataBean.getNamespace();
            if (namespace.equalsIgnoreCase(name)) {
                existName = true;
                tables.addAll(metadataBean.getTables());
            }
        }
        if (existName) {
            date.put("type", "enum");
            date.put("name", name);
            date.put("symbols", tables);
        } else {
            return BaseResultGenerator.failure(HttpStatus.SC_NOT_FOUND,
                    ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                    "Not found, check url please");
        }
        return BaseResultGenerator.success("ok", date);
    }

    private Schema getSchemaByName(String tableName) {
        Map<String, Schema> schemas = getAllSchema();
        return schemas.get(tableName);
    }

    private void updateSchema(String configInfo) {
        if (StrUtil.isBlank(configInfo)) return;
        try {
            Map schemaMap = (Map) JsonMapper.fromJsonString(configInfo, Map.class);
            Object docSchema;
            if (StringUtil.isNotEmpty(docSchema = schemaMap.get("doc"))) {
                schemaMap.put("doc", JsonMapper.toJsonString(docSchema));
            }
            Object fieldsObj;
            if (StringUtil.isNotEmpty(fieldsObj = schemaMap.get("fields"))) {
                List<Map> fields = (List<Map>) fieldsObj;
                for (Map next : fields) {
                    Object docField;
                    if (StringUtil.isEmpty(docField = next.get("doc"))) {
                        continue;
                    }
                    next.put("doc", JsonMapper.toJsonString(docField));
                }
            }
            Schema schema = new Schema.Parser().parse(JSONUtil.toJsonStr(schemaMap));
            allSchemaMap.put(schema.getName(), schema);
        } catch (JSONException | SchemaParseException | ClassCastException e) {
            log.error("update Schema error: {}", e);
        }
    }
}
