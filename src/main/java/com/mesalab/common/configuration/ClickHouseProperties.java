package com.mesalab.common.configuration;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "clickhouse")
public class ClickHouseProperties {
    private String url;

    private String driverClassName;

    private String dbname;

    private String username;

    private String password;

    private String publicKey;

    private Long socketTimeout;




}
