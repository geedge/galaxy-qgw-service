package com.mesalab.qgw.model.api;

import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties;
import com.mesalab.common.nacos.NacosConst;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Data
@Component
@NacosConfigurationProperties(prefix = "druid", dataId = "galaxy-qgw-service", groupId = NacosConst.GALAXY_GROUP, type = ConfigType.YAML, autoRefreshed = true)
public class DruidIoHttpSource implements Serializable {
    private String url;
    private String dbname;
    private String skipEmptyBuckets = "true";
}
