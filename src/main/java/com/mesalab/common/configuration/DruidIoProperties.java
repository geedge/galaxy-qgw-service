package com.mesalab.common.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "druid")
public class DruidIoProperties {
    private  String url;
    private String dbname;

}
