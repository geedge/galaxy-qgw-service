package com.mesalab.qgw.service;


import com.mesalab.common.entity.BaseResult;
import com.mesalab.qgw.model.job.StorageDeletionInfo;

import java.util.LinkedHashMap;
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

    Map<String, List<Object>> getSchemaDataDict(String name);

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
    String getDBEngineByTableName(String tableName);


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
     * @return {@link }
     * @created by wWei
     * @date 2021/7/27 2:41 下午
     */
    LinkedHashMap<String, Object> getSchemaByName(String name);


    /**
     * Desc: update schema
     *
     * @param name
     * @param schemaMap
     * @return
     */
    BaseResult updateSchema(String name, Map<String, Object> schemaMap);

    /**
     * 系统存储配额
     *
     * @return
     */
    BaseResult getStorageQuotaCfg();


    /**
     * 获取每天日志存储变化
     *
     * @param searchStartTime
     * @param searchEndTime
     * @return
     */
    BaseResult dailyTrendOfStorage(String searchStartTime, String searchEndTime);

    /**
     * 数据配额设置：
     * 1. 调度任务
     * 2. schema TTL设置
     *
     * @param list
     * @return
     */
    BaseResult updateStorageQuotaCfg(List<StorageDeletionInfo> list);

    BaseResult cleanDataByType(String type);

    /**
     * 自定义查询ID: 由 Catalog（数据库类型): resultID+query 组成。
     *
     * @param resultId
     * @param query
     * @return {@link String}
     * @created by wWei
     * @date 2021/1/7 6:48 下午
     */
    String getCustomQueryId(String resultId, String query);


}
