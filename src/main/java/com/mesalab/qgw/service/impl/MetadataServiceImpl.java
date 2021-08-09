package com.mesalab.qgw.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONException;
import cn.hutool.json.JSONUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.AbstractListener;
import com.alibaba.nacos.api.exception.NacosException;
import com.google.common.collect.Maps;
import com.mesalab.common.enums.DBTypeEnum;
import com.mesalab.common.enums.MetadataTypeEnum;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.nacos.NacosConfig;
import com.mesalab.common.nacos.NacosConst;
import com.mesalab.qgw.model.api.ClickHouseHttpSource;
import com.mesalab.qgw.model.api.DruidIoHttpSource;
import com.mesalab.qgw.model.api.ElasticsearchHttpSource;
import com.mesalab.qgw.model.metadata.MetadataConfig;
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
    private ConfigService configService;
    @Autowired
    private ClickHouseHttpSource clickHouseHttpSource;
    @Autowired
    private DruidIoHttpSource druidIoHttpSource;
    @Autowired
    private ElasticsearchHttpSource elasticsearchHttpSource;
    @Autowired
    public MetadataConfig metadataConfig;

    private Map<String, Schema> schemaCache = Maps.newHashMap();

    @PostConstruct
    public void init() {
        addMetadataListener();
        log.info("Initializing Schema Tables Complete");
    }

    @Override
    public Map<String, Object> getSchemaInfo(String type, String name) {
        if (MetadataTypeEnum.TABLES.getValue().equals(type)) {
            return loadTables(name);
        } else if (MetadataTypeEnum.FIELDS.getValue().equals(type)) {
            return loadFields(name);
        }
        throw new BusinessException(HttpStatus.SC_NOT_FOUND, ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(), "Not found, check url please");
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
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(),
                    ResultCodeEnum.SQL_SYNTAX_ERROR.getCode(), tableName + " 表未在平台注册，请联系数据平台负责人", null);
        }
        Schema schema;
        if (!StrUtil.isBlankIfStr(schema = schemaCache.get(tableName))) {
            return schema;
        }
        try {
            String content = configService.getConfig(tableName.concat(NacosConst.JSON_SUFFIX), nacosConfig.getGroup(), 3000);
            if (StrUtil.isBlankIfStr(content)) {
                throw new BusinessException(ResultStatusEnum.SERVER_ERROR.getCode(),
                        ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(), tableName + " 获取Schema配置异常", null);
            }
            schemaCache.put(tableName, schema = parseSchema(content));
            configService.addListener(tableName.concat(NacosConst.JSON_SUFFIX), nacosConfig.getGroup(), new AbstractListener() {
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

    private void addMetadataListener() {
        try {
            configService.addListener(NacosConst.META_DATA_ID, nacosConfig.getGroup(), new AbstractListener() {
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

    private Map loadFields(String name) {
        Schema schema = getSchemaByName(name);
        if (StringUtil.isEmpty(schema)) {
            throw new BusinessException(HttpStatus.SC_NOT_FOUND, ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(), "Not found, check url please");
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
        return resultMap;
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
            throw new BusinessException(HttpStatus.SC_NOT_FOUND, ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(), "Not found, check url please");
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
            schemaMap = (Map) JsonMapper.fromJsonString(configInfo, Map.class);
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
        } catch (JSONException | SchemaParseException | ClassCastException e) {
            log.error("update Schema error: {}", e);
        }
        return new Schema.Parser().parse(JSONUtil.toJsonStr(schemaMap));
    }
}
