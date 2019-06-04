package com.mesalab.common.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "hbase")
public class HBaseProperties {
    private String url;
    private String dbName;
    private String tableName;
    private String columnFamily;
    private String columnName;

}
