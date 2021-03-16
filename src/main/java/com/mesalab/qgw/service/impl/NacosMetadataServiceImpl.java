package com.mesalab.qgw.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.DBTypeEnum;
import com.mesalab.common.enums.MetadataTypeEnum;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.nacos.NacosClientConfig;
import com.mesalab.qgw.model.api.ClickHouseHttpSource;
import com.mesalab.qgw.model.api.DruidIoHttpSource;
import com.mesalab.qgw.model.metadata.MetadataBean;
import com.mesalab.qgw.service.MetadataService;
import com.zdjizhi.utils.JsonMapper;
import com.zdjizhi.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @Date: 2021-03-11 15:48
 * @Author : liuyongqiang
 * @ClassName : NacosMetadataServiceImpl
 * @Description : 获取Nacos注册中心元数据实现类
 */
@Slf4j
@Service("nacosMetadataService")
public class NacosMetadataServiceImpl implements MetadataService {

    @Autowired
    private NacosClientConfig nacosClientConfig;
    @Autowired
    private ClickHouseHttpSource clickHouseHttpSource;
    @Autowired
    private DruidIoHttpSource druidIoHttpSource;

    private final String NAMESPACE_AVRO = "nacos-namespace-avro";

    private final String DATA_ID_META_DATA = "meta_data";

    private final String DEFAULT_GROUP = "DEFAULT_GROUP";

    private final String CLICKHOUSE_GROUP = "CLICKHOUSE_GROUP";

    private final String DRUID_GROUP = "DRUID_GROUP";

    public List<MetadataBean> metadataBeans;

    public Map<String, Schema> allSchemaMap = new HashMap<>();

    public Map<String, Schema> clickHouseSchemaMap = new HashMap<>();

    public Map<String, Schema> druidSchemaMap = new HashMap<>();


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

    @Override
    public Map<String, Schema> getClickHouseSchema() {
        return clickHouseSchemaMap;
    }

    @Override
    public Map<String, Schema> getDruidSchema() {
        return druidSchemaMap;
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
            ConfigService configService = NacosFactory.createConfigService(nacosClientConfig.getProperties(NAMESPACE_AVRO));
            String content = configService.getConfig(DATA_ID_META_DATA, DEFAULT_GROUP, 3000);
            metadataBeans = JSONUtil.toList(content, MetadataBean.class);
            configService.addListener(DATA_ID_META_DATA, DEFAULT_GROUP, new Listener() {
                @Override
                public Executor getExecutor() {
                    return null;
                }

                @Override
                public void receiveConfigInfo(String configInfo) {
                    log.info("ReceiveConfigInfo Schema Tables {}", configInfo);
                    metadataBeans = JSONUtil.toList(configInfo, MetadataBean.class);
                }
            });
        } catch (NacosException e) {
            log.info("NacosException:{}", e);
        }
    }

    private void initFields() {
        for (MetadataBean metadataBean : metadataBeans) {
            List<String> tables = metadataBean.getTables();
            for (String table : tables) {
                try {
                    ConfigService configService = NacosFactory.createConfigService(nacosClientConfig.getProperties(NAMESPACE_AVRO));
                    String content = configService.getConfig(table, metadataBean.getGroup(), 3000);
                    addSchema(content, metadataBean);
                    configService.addListener(table, metadataBean.getGroup(), new Listener() {
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
                    log.info("NacosException:{}", e);
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

    private void addSchema(String content, MetadataBean metadataBean) {
        if (StrUtil.isBlank(content)) return;
        Schema schema = new Schema.Parser().parse(content);
        allSchemaMap.put(schema.getName(), schema);

        if (CLICKHOUSE_GROUP.equals(metadataBean.getGroup())) {
            clickHouseSchemaMap.put(schema.getName(), schema);
        } else if (DRUID_GROUP.equals(metadataBean.getGroup())) {
            druidSchemaMap.put(schema.getName(), schema);
        }
    }

    private void updateSchema(String configInfo) {
        if (StrUtil.isBlank(configInfo)) return;
        Schema schema = new Schema.Parser().parse(configInfo);
        allSchemaMap.put(schema.getName(), schema);
        Schema ckSchema = clickHouseSchemaMap.get(schema.getName());
        if (Objects.nonNull(ckSchema)) {
            clickHouseSchemaMap.put(schema.getName(), schema);
        }
        Schema druidSchema = druidSchemaMap.get(schema.getName());
        if (Objects.nonNull(druidSchema)) {
            druidSchemaMap.put(schema.getName(), schema);
        }
    }

}
