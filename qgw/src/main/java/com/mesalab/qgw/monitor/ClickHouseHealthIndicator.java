package com.mesalab.qgw.monitor;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.qgw.model.api.ClickHouseHttpSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
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
        clickHouseHttpSource.getRealTimeAccountSocketTimeOut();

        String queryURL = URLUtil.normalize(clickHouseHttpSource.getUrl() + "/?");

        StringBuilder queryParamBuilder = new StringBuilder("user=")
                .append(clickHouseHttpSource.getRealTimeAccountUserName()).append("&")
                .append("password=").append(clickHouseHttpSource.getRealTimeAccountPin()).append("&")
                .append("database=").append(clickHouseHttpSource.getDbName()).append("&")
                .append("query=").append("select timezone() as timeZone, now() as curTimestamp")
                .append(" FORMAT JSON ;");
        String url = queryURL + queryParamBuilder;
        try {
            ResponseEntity responseEntity = restTemplate.getForEntity(url, Map.class);
            if (responseEntity.getStatusCodeValue() == ResultStatusEnum.SUCCESS.getCode()) {
                Map body = (Map) responseEntity.getBody();
                Map data = (Map) ((List) body.get("data")).get(0);
                String dbTimeZone = data.get("timeZone").toString();
                String curDatetime = data.get("curTimestamp").toString();
                builder.up()
                        .withDetail("app", APP)
                        .withDetail("url", clickHouseHttpSource.getUrl())
                        .withDetail("message", "ok")
                        .withDetail("timeZone", dbTimeZone)
                        .withDetail("curTimestamp", curDatetime);
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
