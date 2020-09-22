package com.mesalab.knowledge.common.utils;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Data
@Component
public class HttpConfig {
    /**
     * 最大连接数400
     */
    @Value("${http.pool.max.connection}")
    private int maxConnectionNum;

    /**
     * 单路由最大连接数80
     */
    @Value("${http.pool.max.per.route}")
    private int maxPerRoute;

    /**
     * 向服务端请求超时时间设置(单位:毫秒)
     */
    @Value("${http.pool.request.timeout}")
    private int serverRequestTimeOut;

    /**
     * 向服务端连接超时时间设置(单位:毫秒)
     */
    @Value("${http.pool.connect.timeout}")
    private int serverConnectTimeOut;

    /**
     * 服务端响应超时时间设置(单位:毫秒)
     */
    @Value("${http.pool.response.timeout}")
    private int serverResponseTimeOut;

    /**
     * Druid查询请求响应时间
     */
    @Value("${druid.socketTimeOut}")
    private int druidSocketTimeOut;

    /**
     * Arango查询请求响应时间
     */
    @Value("${arango.socketTimeOut}")
    private int arangoSocketTimeOut;

    /**
     * ClickHouse长查询请求响应时间
     */
    @Value("${clickhouse.longTermAccount.socketTimeOut}")
    private int ckLongTermAccountSocketTimeOut;

    /**
     * ClickHouse实时查询请求响应时间
     */
    @Value("${clickhouse.realTimeAccount.socketTimeOut}")
    private int ckRealTimeAccountSocketTimeOut;

}
