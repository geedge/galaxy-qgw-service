package com.mesalab.qgw.model.basic;

import com.alibaba.nacos.api.config.annotation.NacosValue;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Data
@Component(value = "hBaseAPISource")
public class HBaseAPISource implements Serializable {
    /**
     * habase查询，ZooKeeper的Quorums机制
     */
    @NacosValue(value = "${hbase.zookeeperQuorum}", autoRefreshed = true)
    private String zookeeperQuorum;
    /**
     * habase查询，ZooKeeper的父节点
     */
    @NacosValue(value = "${hbase.zookeeperZnodeParent}", autoRefreshed = true)
    private String zookeeperZnodeParent;
    /**
     * 数据库名
     */
    @NacosValue(value = "${hbase.report.dbname}", autoRefreshed = true)
    private String dbName;
    /**
     * 表名
     */
    @NacosValue(value = "${hbase.report.tableName}", autoRefreshed = true)
    private String tableName;
    /**
     * habase的列族
     */
    @NacosValue(value = "${hbase.report.columnFamily}", autoRefreshed = true)
    private String columnFamily;
    /**
     * habase的列名
     */
    @NacosValue(value = "${hbase.report.columnName}", autoRefreshed = true)
    private String columnName;
    /**
     * 请求超时时间
     */
    @NacosValue(value = "${hbase.rpcTimeout}", autoRefreshed = true)
    private String rpcTimeout;


}
