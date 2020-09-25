package com.mesalab.qgw.service.impl;

import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.configuration.ClickHouseProperties;
import com.mesalab.common.configuration.DruidIoProperties;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.enums.DBTypeEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.SchemaCacheUtils;
import com.mesalab.qgw.service.MetadataService;
import com.zdjizhi.utils.JsonMapper;
import com.zdjizhi.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;

@Slf4j
@Service("metadataService")
public class MetadataServiceImpl implements MetadataService {

    private static final String METADATA_SCHEMA_KEY = "metadata:schema";

    @Autowired
    ClickHouseProperties clickHouseProperties;
    @Autowired
    DruidIoProperties druidIoProperties;

    @Override
    public BaseResult getSchemaInfo(String type, String name) {
        if ("tables".equals(type)) {
            List<String> tables = getTablesName(name);
            return encapsulationTablesResult(tables, name);
        } else if ("fields".equals(type)) {
            return BaseResultGenerator.success("ok", parseAndGetSchemaMap(name));
        } else {
            return BaseResultGenerator.failure(HttpStatus.SC_NOT_FOUND, ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(), "Not found, check url please");
        }
    }

    private List<String> getTablesName(String dbName) {
        List<String> tables = new ArrayList<>();
        Map<String, Schema> map = (Map<String, Schema>) SchemaCacheUtils.get(METADATA_SCHEMA_KEY);
        map.values().forEach(schema -> {
            Optional.ofNullable(schema).ifPresent(o -> {
                if (dbName.equalsIgnoreCase(schema.getNamespace())) {
                    tables.add(schema.getName());
                }
            });
        });
        return tables;
    }

    private Map parseAndGetSchemaMap(String tableName) {
        Schema schema = getSchemaByName(tableName);
        if (StringUtil.isEmpty(schema)) {
            throw new BusinessException(HttpStatus.SC_NOT_FOUND, ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(), "Unregister In Schema: " + tableName, null);
        }
        Map resultMap = (Map) JsonMapper.fromJsonString(String.valueOf(schema), Map.class);
        Map schemaDoc = (Map) JsonMapper.fromJsonString(schema.getDoc(), Map.class);
        if (StringUtil.isEmpty(schemaDoc) && StringUtil.isNotEmpty(schema.getDoc())) {
            log.error("{} schema's doc isn't jsonString and won't convert: {}", tableName, schema.getDoc());
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

    private Schema getSchemaByName(String tableName) {
        Map<String, Schema> schemas = (Map<String, Schema>) SchemaCacheUtils.get(METADATA_SCHEMA_KEY);
        return schemas.get(tableName);
    }

    /**
     * 封装库中元数据信息
     *
     * @param tables
     * @return
     */
    private BaseResult encapsulationTablesResult(List tables, String database) {
        Map<String, Object> date = new LinkedHashMap<>();
        date.put("type", "enum");
        date.put("name", database);
        date.put("symbols", tables);
        BaseResult<Map> baseResult = BaseResultGenerator.success("ok", date);
        return baseResult;
    }


    /**
     * 读取fileDir目录下的文件(.avsc)
     *
     * @param fileDir
     * @return
     */
    private Map<String, Schema> getSchemasByDirs(File fileDir) {
        Map<String, Schema> result = new HashMap<>();
        List<File> files = recurseDirs(fileDir, ".avsc");
        for (File item : files) {
            try {
                Schema schema = new Schema.Parser().parse(item);
                result.put(schema.getName(), schema);
            } catch (Exception e) {
                log.error("File: {}, Schema Parser Error: {}", item.getPath(), e);
            }
        }
        return result;
    }


    /**
     * 遍历读取startDir文件夹目录下所有以regex结尾的文件
     *
     * @param startDir
     * @param regex
     * @return
     */
    private List<File> recurseDirs(File startDir, String regex) {
        List<File> result = new ArrayList<>();
        Optional.ofNullable(startDir.listFiles()).ifPresent(o -> {
            for (File item : o) {
                if (item.isDirectory()) {
                    result.addAll(recurseDirs(item, regex));
                } else {
                    if (item.getName().endsWith(regex)) {
                        result.add(item);
                    }
                }
            }
        });
        return result;
    }

    @Override
    public String getPartitionKey(String tableName) {
        String partitionKey = StringUtil.EMPTY;
        try {
            Schema schema = getSchemaByName(tableName);
            Map doc = (Map) JsonMapper.fromJsonString(schema.getDoc(), Map.class);
            if (StringUtil.isNotEmpty(doc)) {
                Object partition_key = doc.get("partition_key");
                partitionKey = StringUtil.isEmpty(partition_key) ? StringUtil.EMPTY : partition_key.toString();
            }
        } catch (Exception e) {
            log.error("Get Partition key Error ", e);
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
        if (clickHouseProperties.getDbname().equalsIgnoreCase(schema.getNamespace())
                || "system".equalsIgnoreCase(schema.getNamespace())) {
            return DBTypeEnum.CLICKHOUSE.getValue();
        }
        if (druidIoProperties.getDbname().equalsIgnoreCase(schema.getNamespace())) {
            return DBTypeEnum.DRUID.getValue();
        }
        return StringUtil.EMPTY;
    }

    @Override
    public String getDBNameByTableName(String tableName) {
        Schema schema = getSchemaByName(tableName);
        return StringUtil.isNotEmpty(schema) ? schema.getNamespace() : clickHouseProperties.getDbname() ;
    }


    @Override
    public Map<String, Schema> getAllSchema() {
        try {
            String pathRoot = new File("").getCanonicalPath();
            File fileDir = new File(pathRoot + "/config/avro");
            return getSchemasByDirs(fileDir);
        } catch (Exception e) {
            log.error("Get Schema Error {}", e.getMessage());
            throw new BusinessException(ResultStatusEnum.SERVER_ERROR.getCode(), ResultCodeEnum.UNKNOW_ERROR.getCode(), "Get Schema Error:", e);
        }
    }
}
