package com.mesalab.qgw.service;


import com.mesalab.common.base.BaseResult;
import org.apache.avro.Schema;

import java.util.Map;

public interface MetadataService {

    /**
     * 查询schema信息
     *
     * @param type
     * @param name
     * @return
     */
    BaseResult getSchemaInfo(String type, String name);

    /**
     * 根据表名获取partitionKey
     *
     * @param tableName
     * @return
     */
    String getPartitionKey(String tableName);

    /**
     * Schema Doc中: 通过key获取value
     *
     * @param tableName
     * @param key
     * @return
     */
    String getValueByKeyInSchemaDoc(String tableName, String key);

    /**
     * 通过数据源，获取所属数据库类型
     *
     * @param tableName
     * @return
     */
    String getDBTypeByTableName(String tableName);


    /**
     * 通过数据源，获取所对应数据库名称
     *
     * @param tableName
     * @return
     */
    String getDBNameByTableName(String tableName);


    /**
     * 获取所有已注册schema
     *
     * @return
     */
    Map<String, Schema> getAllSchema();

}
