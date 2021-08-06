package com.mesalab.qgw.model.api;

import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties;
import com.mesalab.common.nacos.NacosConst;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Data
@Component
@NacosConfigurationProperties(prefix = "clickhouse", dataId = "${nacos.config.data-id}", groupId = "${nacos.config.group}", type = ConfigType.YAML, autoRefreshed = true)
public class ClickHouseHttpSource implements Serializable {

    private String url;

    private String dbName;

    private Boolean optimizeTopN;

    private DBAccount realTimeAccount;

    private DBAccount longTermAccount;

    private int enableHttpCompression = 1;

    private String systemDBName = "system";

    private long socketTimeout;

    @Data
    public static class DBAccount {
        private String username;
        private String pin;
    }

}
