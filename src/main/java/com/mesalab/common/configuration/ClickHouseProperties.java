package com.mesalab.common.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "clickhouse")
public class ClickHouseProperties {
    private String url;

    private String driverClassName;

    private DBAccount realTimeAccount;

    private DBAccount longTimeAccount;

}
