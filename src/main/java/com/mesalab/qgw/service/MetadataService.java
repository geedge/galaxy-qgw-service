package com.mesalab.qgw.service;


import com.mesalab.common.base.BaseResult;
import org.apache.avro.JsonProperties;

import java.util.Map;

public interface MetadataService {

    /**
     * 查询schema信息
     * @param type
     * @param name
     * @return
     */
   BaseResult getSchemaInfo(String type, String name);

    /**
     * 根据表名获取partitionKey
     * @param tableName
     * @return
     */
    String getPartitionKey(String tableName);

    /**
     * Schema Doc中: 通过key获取value
     * @param tableName
     * @param key
     * @return
     */
    String getValueByKeyInSchemaDoc(String tableName, String key);

    /**
     * 通过表名获取在哪个数据库
     * @param tableName
     * @return
     */
    String getDBTypeByTableName(String tableName);

    Map<String, JsonProperties> getAllSchema();
}
