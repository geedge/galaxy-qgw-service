package com.mesalab.common.configuration;

import com.mesalab.qgw.model.api.DefaultConfigSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@EnableConfigurationProperties(DefaultConfigProperties.class)
public class DefaultConfiguration {

    private final DefaultConfigProperties defaultConfigProperties;

    @Autowired
    public DefaultConfiguration(DefaultConfigProperties defaultConfigProperties) {
        this.defaultConfigProperties = defaultConfigProperties;
    }

    @Bean(name="defaultConfigSource")
    public DefaultConfigSource defaultConfigSource() {
        DefaultConfigSource defaultConfigSource  = new DefaultConfigSource();
        defaultConfigSource.setMaxNumLimit(defaultConfigProperties.getMaxNumLimit());
        return defaultConfigSource;
    }


















}
