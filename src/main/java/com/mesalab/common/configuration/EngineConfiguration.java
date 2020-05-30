package com.mesalab.common.configuration;

import com.mesalab.qgw.model.api.EngineConfigSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@EnableConfigurationProperties(EngineProperties.class)
public class EngineConfiguration {

    private final EngineProperties engineProperties;

    @Autowired
    public EngineConfiguration(EngineProperties engineProperties) {
        this.engineProperties = engineProperties;
    }

    @Bean(name="engineConfigSource")
    public EngineConfigSource engineConfigSource() {
        EngineConfigSource engineConfigSource  = new EngineConfigSource();
        engineConfigSource.setMaxCacheNum(engineProperties.getMaxCacheNum());
        engineConfigSource.setDefaultResultNum(engineProperties.getDefaultResultNum());
        return engineConfigSource;
    }


















}
