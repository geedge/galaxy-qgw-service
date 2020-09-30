package com.mesalab.qgw.model.api;

import lombok.Data;

import java.io.Serializable;
@Data
public class HBaseAPISource implements Serializable {

    private String zookeeperQuorum;
    private String zookeeperPropertyClientPort;
    private String zookeeperParent;
    private String dbName;
    private String tableName;
    private String columnFamily;
    private String columnName;
    private String clientRetriesNumber;
    private String clientIpcPoolSize;
    private String rpcTimeout;

}
