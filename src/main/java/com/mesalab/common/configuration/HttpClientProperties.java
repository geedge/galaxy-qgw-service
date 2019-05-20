package com.mesalab.common.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "httpclient")
public class HttpClientProperties {

    private Integer connectTimeout = 1000;
    private Integer SocketTimeout = 10000;
    private Integer maxConnection = 10;
    private Integer maxPerRoute = 20;
    private String  userAgent = "Mozilla/5.0(Windows NT 6.1;Win64; x64; rv:50.0) Gecko/20100101 Firefox/50.0";




}
