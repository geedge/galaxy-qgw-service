package com.mesalab.common.nacos;

import com.alibaba.nacos.api.PropertyKeyConst;
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
public class NacosConfig {

    @Value("${nacos.config.server-addr}")
    private String serverAddr;
    @Value("${nacos.config.username}")
    private String username;
    @Value("${nacos.config.password}")
    private String password;

    public Properties getProperties(String namespace) {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, serverAddr);
        properties.put(PropertyKeyConst.USERNAME, username);
        properties.put(PropertyKeyConst.PASSWORD, password);
        properties.put(PropertyKeyConst.NAMESPACE, namespace);
        return properties;
    }
}
