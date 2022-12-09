package com.mesalab.qgw.model.api;

import com.alibaba.nacos.api.config.annotation.NacosValue;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Data
@Component
public class ClickHouseHttpSource implements Serializable {
    /**
     * clickhouse的url地址
     */
    @NacosValue(value = "${clickhouse.url}", autoRefreshed = true)
    private String url;
    /**
     * 数据库名称
     */
    @NacosValue(value = "${clickhouse.dbname}", autoRefreshed = true)
    private String dbName;
    /**
     * 是否开启粗略的优化器
     */
    @NacosValue(value = "${clickhouse.enableApproximateOptimizer}", autoRefreshed = true)
    private boolean enableApproximateOptimizer;
    /**
     * realTime的用户名
     */
    @NacosValue(value = "${clickhouse.realTimeAccount.username}")
    private String realTimeAccountUserName;
    /**
     * realTime的密码
     */
    @NacosValue(value = "${clickhouse.realTimeAccount.pin}")
    private String realTimeAccountPin;
    /**
     * realTime的超时时间
     */
    @NacosValue(value = "${clickhouse.realTimeAccount.socketTimeOut}", autoRefreshed = true)
    private String realTimeAccountSocketTimeOut;
    /**
     * long查询的用户名
     */
    @NacosValue(value = "${clickhouse.longTermAccount.username}")
    private String longTermAccountUserName;
    /**
     * long查询的密码
     */
    @NacosValue(value = "${clickhouse.longTermAccount.pin}")
    private String longTermAccountPin;
    /**
     * long超时时间
     */
    @NacosValue(value = "${clickhouse.longTermAccount.socketTimeOut}", autoRefreshed = true)
    private String longTermAccountSocketTimeOut;

    private int enableHttpCompression = 1;

    private String systemDBName = "system";


}
