package com.mesalab.common.configuration;

import com.mesalab.qgw.model.api.JobAdminHttpSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@EnableConfigurationProperties(JobAdminProperties.class)
public class JobAdminConfiguration {

    private final JobAdminProperties jobAdminProperties;

    @Autowired
    public JobAdminConfiguration(JobAdminProperties jobAdminProperties) {
        this.jobAdminProperties = jobAdminProperties;
    }

    @Bean(name = "jobAdminHttpSource")
    public JobAdminHttpSource jobAdminHttpSource() {
        JobAdminHttpSource httpSource = new JobAdminHttpSource();
        httpSource.setUrl(jobAdminProperties.getUrl());
        httpSource.setUserName(jobAdminProperties.getUserName());
        httpSource.setPassword(jobAdminProperties.getPassword());
        return httpSource;
    }
}