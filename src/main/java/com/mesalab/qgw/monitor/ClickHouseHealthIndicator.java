package com.mesalab.qgw.monitor;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import com.mesalab.qgw.model.api.ClickHouseHttpSource;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class ClickHouseHealthIndicator extends AbstractHealthIndicator {


    public RestTemplate restTemplate = new RestTemplate();
    @Autowired
    private ClickHouseHttpSource clickHouseHttpSource;
    private final static String APP = "ClickHouse";

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        if (StrUtil.contains(clickHouseHttpSource.getUrl(), "127.0.0.1")) {
            builder.up()
                    .withDetail("app", APP)
                    .withDetail("message", "DISABLED");
            return;
        }
        clickHouseHttpSource.getRealTimeAccount();

        String queryURL = URLUtil.normalize(clickHouseHttpSource.getUrl() + "/?");

        StringBuilder queryParamBuilder = new StringBuilder("user=")
                .append(clickHouseHttpSource.getRealTimeAccount().getUsername()).append("&")
                .append("password=").append(clickHouseHttpSource.getRealTimeAccount().getPin()).append("&")
                .append("database=").append(clickHouseHttpSource.getDbName()).append("&")
                .append("query=").append("select version()")
                .append(" FORMAT JSON ;");
        String url = queryURL + queryParamBuilder.toString();

        try {
            ResponseEntity responseEntity = restTemplate.getForEntity(url, Map.class);

            if (responseEntity.getStatusCodeValue() == HttpStatus.SC_OK) {
                builder.up()
                        .withDetail("app", APP)
                        .withDetail("url", clickHouseHttpSource.getUrl())
                        .withDetail("message", "ok");
            } else {
                builder.down()
                        .withDetail("app", APP)
                        .withDetail("message", "error, status code " + responseEntity.getStatusCodeValue());


            }
        } catch (RuntimeException e) {
            builder.down()
                    .withDetail("app", APP)
                    .withDetail("message", e.getMessage());

        }

    }


}
