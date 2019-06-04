package com.mesalab.common.configuration;

import com.mesalab.qgw.model.api.HBaseHttpSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@EnableConfigurationProperties(HBaseProperties.class)
public class HBaseConfiguration {

    private final HBaseProperties hBaseProperties;

    @Autowired
    public HBaseConfiguration(HBaseProperties hBaseProperties) {
        this.hBaseProperties = hBaseProperties;
    }


    @Bean(name="hBaseHttpSource")
    public HBaseHttpSource hBaseHttpSource() {
        HBaseHttpSource httpSource  = new HBaseHttpSource();
        httpSource.setUrl(hBaseProperties.getUrl());
        httpSource.setDbName(hBaseProperties.getDbName());
        httpSource.setTableName(hBaseProperties.getTableName());
        httpSource.setColumnFamily(hBaseProperties.getColumnFamily());
        httpSource.setColumnName(hBaseProperties.getColumnName());

        return httpSource;
    }

}
