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
     * 根据表明获取PrimaryKey
     * @param tableName
     * @return
     */
    String getPrimaryKey(String tableName);


    Map<String, JsonProperties> getAllSchema();
}
