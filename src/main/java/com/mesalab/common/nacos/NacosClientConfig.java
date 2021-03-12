package com.mesalab.common.nacos;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * @Date: 2021-03-11 17:36
 * @Author : liuyongqiang
 * @ClassName : NacosClientConfig
 * @Description : NacosClientConfig
 */
@Data
@Component
public class NacosClientConfig {

    @Value("${nacos.config.server-addr}")
    private String serverAddr;
    @Value("${nacos.config.username}")
    private String username;
    @Value("${nacos.config.password}")
    private String password;

    public Properties getProperties(String namespace) {
        Properties properties = new Properties();
        properties.put("serverAddr", serverAddr);
        properties.put("username", username);
        properties.put("password", password);
        properties.put("namespace", namespace);
        return properties;
    }
}
