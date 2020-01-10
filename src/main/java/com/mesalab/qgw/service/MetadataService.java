package com.mesalab.qgw.service;


import com.mesalab.common.base.BaseResult;

public interface MetadataService {

    /**
     * 查询schema信息
     * @param type
     * @param name
     * @return
     */
   BaseResult getSchemaInfo(String type, String name);

    /**
     * 根据表面获取partitionKey
     * @param tableName
     * @return
     */
    String getPartitionKey(String tableName);
}
