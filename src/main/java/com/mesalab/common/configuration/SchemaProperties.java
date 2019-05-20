package com.mesalab.common.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "schema")
public class SchemaProperties {
    private String clickhouse;
    private String druid;
    private  int  defaultMaxRows;

}
