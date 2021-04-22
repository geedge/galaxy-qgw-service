package com.mesalab.qgw.monitor;

import cn.hutool.core.util.URLUtil;
import com.mesalab.common.nacos.NacosConfig;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Nacos健康检测
 *
 * @Classname NacosHealthIndicator
 * @Date 2021/4/22 1:57 下午
 * @Author wWei
 */
@Component
public class NacosHealthIndicator extends AbstractHealthIndicator {

    @Autowired
    private NacosConfig nacosConfig;
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        try {
            String queryURL = URLUtil.normalize(nacosConfig.getServerAddr() + "/nacos/v1/auth/users/login?");
            StringBuilder queryParamBuilder = new StringBuilder("username=")
                    .append(nacosConfig.getUsername()).append("&")
                    .append("password=").append(nacosConfig.getPassword());

            ResponseEntity responseEntity = restTemplate.postForEntity(queryURL + queryParamBuilder, null, Map.class);
            if (responseEntity.getStatusCodeValue() == HttpStatus.SC_OK) {
                builder.up()
                        .withDetail("app", "Nacos")
                        .withDetail("url", nacosConfig.getServerAddr())
                        .withDetail("message", "ok");
            } else {
                builder.down()
                        .withDetail("app", "Nacos")
                        .withDetail("message", "error, status code " + responseEntity.getStatusCodeValue());
            }
        } catch (RuntimeException e) {
            builder.down()
                    .withDetail("app", "Nacos")
                    .withDetail("message", e.getMessage());
        }
    }
}
