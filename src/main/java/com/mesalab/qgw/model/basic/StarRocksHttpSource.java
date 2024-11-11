package com.mesalab.qgw.model.basic;

import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties;
import com.alibaba.nacos.api.config.annotation.NacosValue;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * @author wangwei
 */
@Data
@Component
public class StarRocksHttpSource implements Serializable {
    @NacosValue(value = "${starRocks.url}", autoRefreshed = true)
    private String url;
    @NacosValue(value = "${starRocks.dbname}", autoRefreshed = true)
    private String dbname;
    @NacosValue(value = "${starRocks.username}", autoRefreshed = true)
    private String username;
    @NacosValue(value = "${starRocks.pin}", autoRefreshed = true)
    private String pin;
    @NacosValue(value = "${starRocks.socketTimeOut}", autoRefreshed = true)
    private Integer socketTimeOut;
}
