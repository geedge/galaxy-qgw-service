package com.mesalab.qgw.model.api;

import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Data
@Component(value = "hBaseAPISource")
@NacosConfigurationProperties(prefix = "hbase", dataId = "${nacos.config.data-id}", groupId = "${nacos.config.group}", type = ConfigType.YAML, autoRefreshed = true)
public class HBaseAPISource implements Serializable {

    private String zookeeperQuorum;
    private String zookeeperZnodeParent;
    private String dbName;
    private String tableName;
    private Report report;
    private String rpcTimeout;

    @Data
    public static class Report{
        private String columnFamily;
        private String columnName;
        private String clientIpcPoolSize;
    }

}
