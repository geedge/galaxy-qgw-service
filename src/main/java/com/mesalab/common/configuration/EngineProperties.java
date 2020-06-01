package com.mesalab.common.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@ConfigurationProperties(prefix = "engine")
public class EngineProperties {
    private int maxCacheNum;
    private int defaultResultNum;
}
