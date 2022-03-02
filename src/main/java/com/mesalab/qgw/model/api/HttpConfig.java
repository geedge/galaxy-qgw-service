package com.mesalab.qgw.model.api;

import com.alibaba.nacos.api.config.annotation.NacosValue;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Data
@Component
public class HttpConfig implements Serializable {
    /**
     * 最大连接数400
     */
    @NacosValue(value = "${http.pool.max.connection}", autoRefreshed = true)
    private int maxConnectionNum;

    /**
     * 单路由最大连接数80
     */
    @NacosValue(value = "${http.pool.max.per.route}", autoRefreshed = true)
    private int maxPerRoute;

    /**
     * 向服务端请求超时时间设置(单位:毫秒)
     */
    @NacosValue(value = "${http.pool.request.timeout}", autoRefreshed = true)
    private int serverRequestTimeOut;

    /**
     * 向服务端连接超时时间设置(单位:毫秒)
     */
    @NacosValue(value = "${http.pool.connect.timeout}", autoRefreshed = true)
    private int serverConnectTimeOut;

    /**
     * 服务端响应超时时间设置(单位:毫秒)
     */
    @NacosValue(value = "${http.pool.response.timeout}", autoRefreshed = true)
    private int serverResponseTimeOut;

    /**
     * Druid查询请求响应时间
     */
    @NacosValue(value = "${druid.socketTimeOut}", autoRefreshed = true)
    private int druidSocketTimeOut;

    /**
     * Arango查询请求响应时间
     */
    @NacosValue(value = "${arango.socketTimeOut}", autoRefreshed = true)
    private int arangoSocketTimeOut;

    /**
     * ClickHouse长查询请求响应时间
     */
    @NacosValue(value = "${clickhouse.longTermAccount.socketTimeOut}", autoRefreshed = true)
    private int ckLongTermAccountSocketTimeOut;

    /**
     * ClickHouse实时查询请求响应时间
     */
    @NacosValue(value = "${clickhouse.realTimeAccount.socketTimeOut}", autoRefreshed = true)
    private int ckRealTimeAccountSocketTimeOut;
    /**
     * Desc: Elasticsearch查询请求响应时间
     */
    @NacosValue(value = "${elasticsearch.socketTimeOut}", autoRefreshed = true)
    private int elasticsearchSocketTimeOut;





}
