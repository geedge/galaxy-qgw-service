package com.mesalab.common.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "clickhouse")
public class ClickHouseProperties {
    private String url;
    private String driverClassName;
    private String dbname;

    private DBAccount realTimeAccount = new DBAccount();

    private DBAccount longTermAccount = new DBAccount();

    @Data
    public class DBAccount {
        private String username;
        private String userKey;

    }

}
