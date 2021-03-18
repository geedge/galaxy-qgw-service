package com.mesalab.knowledge.common.config;

import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * @description: for http
 * @author: zhq
 * @create: 2020-07-07
 **/
@Data
@Component
@NacosConfigurationProperties(prefix = "arango", dataId = "galaxy-qgw-service", type = ConfigType.YAML, autoRefreshed = true)
public class ArangoConfig {

    private String server;

    private String database;

    private String username;

    private String password;

    private String jwturl;

    private String queryurl;

    private String maxrows;

    private String jwt;

}
