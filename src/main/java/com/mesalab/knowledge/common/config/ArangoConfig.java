package com.mesalab.knowledge.common.config;

import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties;
import com.mesalab.common.nacos.NacosConst;
import org.springframework.stereotype.Component;

/**
 * @description: for http
 * @author: zhq
 * @create: 2020-07-07
 **/
@Component
@NacosConfigurationProperties(prefix = "arango", dataId = "${nacos.config.data-id}", groupId = "${nacos.config.group}", type = ConfigType.YAML, autoRefreshed = true)
public class ArangoConfig {

    private String server;

    private String database;

    private String username;

    private String pin;

    private String jwturl;

    private String queryurl;

    private String maxrows;

    private String jwt;

    public String getServer() {
        return server;
    }

    public void setServer(String server) {
        this.server = server;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPin() {
        return pin;
    }

    public void setPin(String pin) {
        this.pin = pin;
    }

    public String getJwturl() {
        return jwturl;
    }

    public void setJwturl(String jwturl) {
        this.jwturl = jwturl;
    }

    public String getQueryurl() {
        return queryurl;
    }

    public void setQueryurl(String queryurl) {
        this.queryurl = queryurl;
    }

    public String getMaxrows() {
        return maxrows;
    }

    public void setMaxrows(String maxrows) {
        this.maxrows = maxrows;
    }

    public String getJwt() {
        return jwt;
    }

    public void setJwt(String jwt) {
        this.jwt = jwt;
    }
}
