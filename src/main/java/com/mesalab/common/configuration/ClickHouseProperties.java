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

    private DBAccount realTimeAccount = new DBAccount();

    private DBAccount longTermAccount = new DBAccount();

    public class DBAccount {
        private String username;
        private String userKey;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getUserKey() {
            return userKey;
        }

        public void setUserKey(String userKey) {
            this.userKey = userKey;
        }
    }

}
