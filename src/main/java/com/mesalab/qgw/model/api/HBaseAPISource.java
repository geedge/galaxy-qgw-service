package com.mesalab.qgw.model.api;

import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Data
@Component
@NacosConfigurationProperties(prefix = "hbase", dataId = "galaxy-qgw-service", type = ConfigType.YAML, autoRefreshed = true)
public class HBaseAPISource implements Serializable {

    private String zookeeperQuorum;
    private String zookeeperPropertyClientPort;
    private String zookeeperZnodeParent;
    private String dbName;
    private String tableName;
    private String columnFamily;
    private String columnName;
    private String clientIpcPoolSize;
    private String rpcTimeout;

}
