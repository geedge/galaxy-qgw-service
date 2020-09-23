package com.mesalab.common.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "hbase")
public class HBaseProperties {
    private String zookeeperQuorum;
    private String zookeeperPropertyClientPort;
    private String dbName;
    private String tableName;
    private String columnFamily;
    private String columnName;
    private String clientRetriesNumber;
    private String rpcTimeout;
}
