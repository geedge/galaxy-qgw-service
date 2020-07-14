package com.mesalab.knowledge.common.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * @description: for http
 * @author: zhq
 * @create: 2020-07-07
 **/
@Data
@Configuration
public class ArangoConfig {

    @Value("${arango.server}")
    private String server;

    @Value("${arango.database}")
    private String database;

    @Value("${arango.username}")
    private String username;

    @Value("${arango.password}")
    private String password;

    private String jwt;

}
