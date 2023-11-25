package com.mesalab.services.common.entity;

import com.alibaba.nacos.api.config.annotation.NacosValue;
import lombok.Data;
import org.springframework.context.annotation.Configuration;


/**
 * @Author wxs
 * @Date 2022/7/25
 */
@Data
@Configuration
public class KnowledgeConfig {
    @NacosValue(value = "${knowledge.ipLocation.userDefined}", autoRefreshed = true)
    private String ipUserDefined;
    @NacosValue(value = "${knowledge.ipLocation.builtIn}", autoRefreshed = true)
    private String ipBuiltIn;
    @NacosValue(value = "${knowledge.ipLocation.asn}", autoRefreshed = true)
    private String ipAsn;
}
