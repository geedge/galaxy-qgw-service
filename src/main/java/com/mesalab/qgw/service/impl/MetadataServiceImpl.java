package com.mesalab.qgw.service.impl;

import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.configuration.ClickHouseProperties;
import com.mesalab.common.configuration.DruidIoProperties;
import com.mesalab.common.enums.BaseResultEnum;
import com.mesalab.common.enums.QueryOptionEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.CacheBaseUtils;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.service.MetadataService;
import com.zdjizhi.utils.JsonMapper;
import com.zdjizhi.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.JsonProperties;
import org.apache.avro.Schema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;

@Slf4j
@Service("metadataServiceImpl")
public class MetadataServiceImpl implements MetadataService {

    @Autowired
    ClickHouseProperties clickHouseProperties;
    @Autowired
    DruidIoProperties druidIoProperties;

    @Override
    public BaseResult getSchemaInfo(String type, String name) {

        ApiParam param = new ApiParam();
        param.setOption(QueryOptionEnum.QUERY_SCHEMA.getValue());
        if("tables".equals(type)){//查询库中所有表
            if(clickHouseProperties.getDbname().equals(name)){
                return encapsulationTablesResult(new ArrayList<>(getSchemasOfCK().keySet()) , clickHouseProperties.getDbname());
            }
            if(druidIoProperties.getDbname().equals(name)){
                return encapsulationTablesResult(new ArrayList<>(getSchemasOfDruid().keySet()) , druidIoProperties.getDbname());
            }
            throw new BusinessException(BaseResultEnum.NOT_FOUND.getCode(), "check DBName please", null);
        }else if("fields".equals(type)){//查询表字段具体信息
            return getSchemaResult(name);
        }else {
            throw new BusinessException(BaseResultEnum.NOT_FOUND.getCode(), "Not found, check url please", null);
        }
    }



    /**
     * 获取schema信息
     * @param tableName
     * @return
     */
    public BaseResult getSchemaResult(String tableName) {
        Map<String, JsonProperties> allSchema;
        Object allSchemasInfo = CacheBaseUtils.get("metadata:schema");
        if(StringUtil.isNotEmpty(allSchemasInfo)){
            allSchema = (Map<String, JsonProperties>)allSchemasInfo;
        }else {
            allSchema = getAllSchema();
        }
        Map resultMap = (Map) JsonMapper.fromJsonString(String.valueOf(allSchema.get(tableName)), Map.class);
        if (resultMap == null) {
            throw new BusinessException(BaseResultEnum.FAIL.getCode(), "schema未注册此表: " + tableName, null);
        }
        Map schemaDoc = (Map) JsonMapper.fromJsonString(String.valueOf(resultMap.get("doc")), Map.class);
        if(StringUtil.isEmpty(schemaDoc) && !StringUtil.isEmpty(resultMap.get("doc"))){
            log.error("{}表该的 doc 为非JSON字符串{}，未转换", tableName, resultMap.get("doc"));
        }
        if (StringUtil.isNotEmpty(schemaDoc)) {
            resultMap.put("doc", schemaDoc);
        }
        List<Map> fields = (List<Map>) resultMap.get("fields");
        Map mapDoc = null;
        for (Map next : fields) {
            try {
                mapDoc = (Map) JsonMapper.fromJsonString(String.valueOf(next.get("doc")), Map.class);
            } catch (Exception ex) {
                log.debug("该字段的 doc 为非JSON字符串，未转换");
            }
            if (mapDoc != null) {
                next.put("doc", mapDoc);
            }
            if(StringUtil.isEmpty(mapDoc) && !StringUtil.isEmpty(next.get("doc"))){
                log.error("{}表该字段{}的 doc 为非JSON字符串，未转换", tableName, next.get("name"));
            }
        }
        BaseResult<Map> baseResult = BaseResultGenerator.success("ok", resultMap);
        if (!baseResult.isSuccess()) {
            throw new BusinessException(baseResult.getStatus(), baseResult.getMessage(), null);
        }
        return baseResult;
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
     * 获取clickhouse Schema
     * @return
     */
    private Map<String, JsonProperties> getSchemasOfCK(){
        Map<String, JsonProperties> ckSchemas = getSchemasByPath("/config/avro/clickhouse");
        return ckSchemas;
    }

    /**
     * 获取druid Schema
     * @return
     */
    private Map<String, JsonProperties> getSchemasOfDruid(){
        Map<String, JsonProperties> druidschemas = getSchemasByPath("/config/avro/druid");
        return druidschemas;
    }

    /**
     * 获取所有schemas
     * @return
     */
    private Map<String, JsonProperties> getAllSchema(){
        HashMap<String, JsonProperties> result = new HashMap<>();
        Map<String, JsonProperties> schemasOfCK = getSchemasOfCK();
        if(StringUtil.isNotEmpty(schemasOfCK)){
            result.putAll(schemasOfCK);
        }
        Map<String, JsonProperties> schemasOfDruid = getSchemasOfDruid();
        if(StringUtil.isNotEmpty(schemasOfDruid)){
            result.putAll(schemasOfDruid);
        }
        CacheBaseUtils.set("metadata:schema", result, 1000 * 86400);
        return result;
    }


    /**
     * 获取当前路径下的所有schema信息
     * @param path
     * @return
     */
    private Map<String, JsonProperties> getSchemasByPath(String path){
        Map<String, JsonProperties> cKSchema = new HashMap<>();
        String pathRoot= null;
        try {
            pathRoot = new File("").getCanonicalPath();
            File file = new File(pathRoot + path);
            File[] files = file.listFiles();
            if(StringUtil.isEmpty(files)){
                return cKSchema;
            }
            for (File f : files) {
                if(!f.getName().toLowerCase().endsWith(".avsc")){
                    continue;
                }
                try {
                    Schema ckSchema = new Schema.Parser().parse(new File(f.getPath()));
                    cKSchema.put(ckSchema.getName(), ckSchema);
                }catch (Exception ex){
                    log.error("遍历{}路径下文件{}失败: {}", path, f.getName(), ex);
                }
            }
        } catch (Exception e) {
            log.error("获取 项目路径/{}失败", path);
        }
        return cKSchema;
    }

    @Override
    public String getPartitionKey(String tableName){
        String cacheKey = tableName + ":partition_key";
        Object value = CacheBaseUtils.get(cacheKey);
        if (value == null) {
            String v = "";
            try {
                BaseResult schemaResult = getSchemaResult(tableName);
                Map data = (Map)schemaResult.getData();
                Map doc = (Map)data.get("doc");
                if(StringUtil.isNotEmpty(doc)){
                    Object partition_key = doc.get("partition_key");
                    v = StringUtil.isEmpty(partition_key) ? v : partition_key.toString();
                }
                CacheBaseUtils.set(cacheKey, v, 1000 * 86400);
            }catch (Exception e){
                log.error("获取partition_key异常", e);
            }
            return v;
        }
        return value.toString();
    }
}
