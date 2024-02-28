package com.mesalab.qgw.model.basic;

import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Data
@Component
@NacosConfigurationProperties(prefix = "druid", dataId = "${nacos.config.data-id}", groupId = "${nacos.config.group}", type = ConfigType.YAML, autoRefreshed = true)
public class DruidIoHttpSource implements Serializable {
    private String url;
    private String dbname;
    private String skipEmptyBuckets = "true";
    private Integer maxSubqueryRows;
}
