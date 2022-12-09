package com.mesalab.qgw.model.api;

import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * @author wangwei
 */
@Data
@Component("elasticsearchHttpSource")
@NacosConfigurationProperties(prefix = "elasticsearch", dataId = "${nacos.config.data-id}", groupId = "${nacos.config.group}", type = ConfigType.YAML, autoRefreshed = true)
public class ElasticsearchHttpSource implements Serializable {

    private String url;
    private String dbname;
    private String socketTimeOut;
}
