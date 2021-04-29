package com.mesalab.qgw.monitor;

import cn.hutool.core.util.URLUtil;
import cn.hutool.json.JSONUtil;
import com.jayway.jsonpath.JsonPath;
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

            ResponseEntity responseEntity = restTemplate.getForEntity(URLUtil.normalize(nacosConfig.getServerAddr() + "/nacos/actuator/health"), Map.class);
            if (responseEntity.getStatusCodeValue() == HttpStatus.SC_OK
                    && "UP".equalsIgnoreCase(JsonPath.read(JSONUtil.toJsonStr(responseEntity.getBody()), "$.status"))) {
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
