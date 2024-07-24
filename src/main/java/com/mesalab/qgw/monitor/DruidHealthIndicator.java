package com.mesalab.qgw.monitor;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import com.alibaba.fastjson2.JSON;
import com.google.common.collect.Maps;
import com.mesalab.common.enums.HttpStatusCodeEnum;
import com.mesalab.qgw.model.basic.DruidIoHttpSource;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class DruidHealthIndicator extends AbstractHealthIndicator {


    public RestTemplate restTemplate = new RestTemplate();
    @Autowired
    private DruidIoHttpSource druidIoHttpSource;
    private final static String APP = "Druid";

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        if (StrUtil.contains(druidIoHttpSource.getUrl(), "127.0.0.1")) {
            builder.up()
                    .withDetail("app", APP)
                    .withDetail("message", "DISABLED");
            return;
        }
        String queryURL = URLUtil.normalize(druidIoHttpSource.getUrl());
        DruidHealthIndicator.DruidQueryParam druidQueryParam = new DruidHealthIndicator.DruidQueryParam();

        druidQueryParam.setQuery("select TIME_FORMAT(CURRENT_TIMESTAMP,'z') as timeZone, CURRENT_TIMESTAMP as curTimestamp");
        druidQueryParam.getContext().put("skipEmptyBuckets", druidIoHttpSource.getSkipEmptyBuckets());
        druidQueryParam.setResultFormat("object");
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity request = new HttpEntity(JSON.toJSONString(druidQueryParam), headers);
            ResponseEntity responseEntity = restTemplate.postForEntity(queryURL, request, Object.class);

            if (responseEntity.getStatusCodeValue() == HttpStatusCodeEnum.SUCCESS.getCode()) {
                List<Map<String, Object>> body = Optional.ofNullable(responseEntity.getBody())
                        .map(b -> (List<Map<String, Object>>) b)
                        .orElseThrow(() -> new IllegalArgumentException("Response body is null or not a List"));
                Map<String, Object> firstElement = Optional.ofNullable(body.get(0))
                        .orElseThrow(() -> new IllegalArgumentException("First element in the body is null"));
                Object timeZone = firstElement.get("timeZone");
                Object curDatetime = firstElement.get("curTimestamp");
                builder.up()
                        .withDetail("app", APP)
                        .withDetail("url", druidIoHttpSource.getUrl())
                        .withDetail("message", "ok")
                        .withDetail("timeZone", timeZone)
                        .withDetail("timestamp", curDatetime);
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

    @Data
    class DruidQueryParam {
        private String query;
        private Map<String, String> context = Maps.newHashMap();
        private String resultFormat;

    }


}
