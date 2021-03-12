package com.mesalab.qgw.model.api;

import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Data
@Component
@NacosConfigurationProperties(prefix = "engine", dataId = "galaxy-qgw-service", type = ConfigType.YAML, autoRefreshed = true)
public class EngineConfigSource implements Serializable {

    private int maxCacheNum;
    private int defaultResultNum;

}
