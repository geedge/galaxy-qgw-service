package com.mesalab.qgw.config;

import com.alibaba.nacos.api.config.annotation.NacosValue;
import lombok.Data;
import org.springframework.stereotype.Component;

/**
 * @description: for http
 * @author: zhq
 * @create: 2020-07-07
 **/
@Data
@Component
public class ArangoConfig {
    /**
     * url地址
     */
    @NacosValue(value = "${arango.server}", autoRefreshed = true)
    private String server;
    /**
     * 数据库
     */
    @NacosValue(value = "${arango.database}", autoRefreshed = true)
    private String database;
    /**
     * 用户名
     */
    @NacosValue(value = "${arango.username}")
    private String username;
    /**
     * 密码
     */
    @NacosValue(value = "${arango.pin}")
    private String pin;
    /**
     * 获取jwt的url
     */
    @NacosValue(value = "${arango.jwturl}", autoRefreshed = true)
    private String jwturl;
    /**
     * 查询的url
     */
    @NacosValue(value = "${arango.queryurl}", autoRefreshed = true)
    private String queryurl;
    /**
     * 最大行
     */
    @NacosValue(value = "${arango.maxrows}", autoRefreshed = true)
    private String maxrows;
    /**
     * 超时时间
     */
    @NacosValue(value = "${arango.socketTimeOut}", autoRefreshed = true)
    private String socketTimeOut;
}
