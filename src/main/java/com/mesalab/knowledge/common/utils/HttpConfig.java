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
    @Value("${http.pool.druid.timeout}")
    private int druidResponseTimeOut;

    /**
     * Arango查询请求响应时间
     */
    @Value("${http.pool.arango.timeout}")
    private int arangoResponseTimeOut;

    /**
     * ClickHouse长查询请求响应时间
     */
    @Value("${http.pool.clickHouseLongTerm.timeout}")
    private int clickHouseLongTermResponseTimeOut;

    /**
     * ClickHouse实时查询请求响应时间
     */
    @Value("${http.pool.clickHouseRealTime.timeout}")
    private int clickHouseRealTimeResponseTimeOut;

}
