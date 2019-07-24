package com.mesalab.common.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "test")
public class TestProperties {

    private String url;

    private String timeRange;

    private String executeTIme;



}
