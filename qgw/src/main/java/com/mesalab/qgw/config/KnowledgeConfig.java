package com.mesalab.qgw.config;

import com.alibaba.nacos.api.config.annotation.NacosValue;
import lombok.Data;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;


/**
 * @Author wxs
 * @Date 2022/7/25
 */
@Data
@Component
public class KnowledgeConfig {
    @NacosValue(value = "${knowledge.ipLocation.ipV4.userDefined}", autoRefreshed = true)
    private String ipV4UserDefined;
    @NacosValue(value = "${knowledge.ipLocation.ipV4.builtIn}", autoRefreshed = true)
    private String ipV4BuiltIn;
    @NacosValue(value = "${knowledge.ipLocation.ipV4.asn}", autoRefreshed = true)
    private String ipV4Asn;
    @NacosValue(value = "${knowledge.ipLocation.ipV6.userDefined}", autoRefreshed = true)
    private String ipV6UserDefined;
    @NacosValue(value = "${knowledge.ipLocation.ipV6.builtIn}", autoRefreshed = true)
    private String ipV6BuiltIn;
    @NacosValue(value = "${knowledge.ipLocation.ipV6.asn}", autoRefreshed = true)
    private String ipV6Asn;
}
