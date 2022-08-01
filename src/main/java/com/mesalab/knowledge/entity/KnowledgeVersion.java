package com.mesalab.knowledge.entity;

import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties;
import com.alibaba.nacos.api.config.annotation.NacosValue;
import lombok.Data;
import org.springframework.stereotype.Component;


/**
 * @Author wxs
 * @Date 2022/7/25
 */
@Data
@Component
@NacosConfigurationProperties(dataId = "galaxy-qgw-service.yml", groupId = "${nacos.config.group}", type = ConfigType.YAML, autoRefreshed = true)
public class KnowledgeVersion {

    @NacosValue(value = "${knowledge.ipLocation.ipV4.userDefined}", autoRefreshed = true)
    private String ipV4UserDefined;

    @NacosValue(value = "${knowledge.ipLocation.ipV4.builtIn}", autoRefreshed = true)
    private String ipV4BuiltIn;

    @NacosValue(value = "${knowledge.ipLocation.ipV6.userDefined}", autoRefreshed = true)
    private String ipV6UserDefined;

    @NacosValue(value = "${knowledge.ipLocation.ipV6.builtIn}", autoRefreshed = true)
    private String ipV6BuiltIn;

}
