package com.mesalab.common.configuration;

import com.mesalab.qgw.model.api.DruidIoHttpSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@EnableConfigurationProperties(DruidIoProperties.class)
public class DruidIoConfiguration {

    private final DruidIoProperties druidIoProperties;

    @Autowired
    public DruidIoConfiguration(DruidIoProperties druidIoProperties) {
        this.druidIoProperties = druidIoProperties;
    }


    @Bean(name="druidIoHttpSource")
    public DruidIoHttpSource druidIoHttpSource() {
        DruidIoHttpSource httpSource  = new DruidIoHttpSource();
        httpSource.setUrl(druidIoProperties.getUrl());
        httpSource.setDbname(druidIoProperties.getDbname());
        httpSource.setSqlTimeZone(druidIoProperties.getSqlTimeZone());
        return httpSource;
    }



}
