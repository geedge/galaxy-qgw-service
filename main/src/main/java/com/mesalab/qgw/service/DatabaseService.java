package com.mesalab.qgw.service;


import com.mesalab.common.entity.BaseResult;
import org.apache.avro.Schema;
import java.util.List;
import java.util.Map;

public interface DatabaseService {

    /**
     * 查询schema信息
     *
     * @param type
     * @param name
     * @return
     */
    Map getSchemaInfo(String type, String name, boolean displayStorageSize);

    /**
     * 查询字典信息
     *
     * @param name
     * @return
     */
    Object getCfg(String name);

    /**
     * 根据表名获取partitionKey
     *
     * @param tableName
     * @return
     */
    String getPartitionKey(String tableName);

    /**
     * 根据表名获取indexKey
     *
     * @param tableName
     * @return
     */
    List<String> getIndexKey(String tableName);


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
    List<String> getAllTable();

    /**
     * Desc: 通过表名获取Schema
     *
     * @param name
     * @return {@link Schema}
     * @created by wWei
     * @date 2021/7/27 2:41 下午
     */
    Schema getSchemaByName(String name);



    /**
     * Desc: update schema
     *
     * @param name
     * @param schemaMap
     * @return
     */
    BaseResult updateSchema(String name, Map<String, Object> schemaMap);


}
