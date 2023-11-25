package com.mesalab.qgw.monitor;

import cn.hutool.core.util.StrUtil;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.qgw.model.basic.JobAdminHttpSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
public class JobAdminHealthIndicator extends AbstractHealthIndicator {


    public RestTemplate restTemplate = new RestTemplate();
    @Autowired
    private JobAdminHttpSource jobAdminHttpSource;
    private final static String APP = "xxl-job-admin";

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        if (StrUtil.contains(jobAdminHttpSource.getUrl(), "127.0.0.1")) {
            builder.up()
                    .withDetail("app", APP)
                    .withDetail("message", "DISABLED");
            return;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("userName", jobAdminHttpSource.getUserName());
        map.add("pin", jobAdminHttpSource.getPin());

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        try {
            ResponseEntity responseEntity = restTemplate.postForEntity(jobAdminHttpSource.getUrl() + "/login?", request, Map.class);
            if (responseEntity.getStatusCodeValue() == ResultStatusEnum.SUCCESS.getCode()) {
                Map body = (HashMap) responseEntity.getBody();
                int code = Integer.parseInt(String.valueOf(body.get("code")));
                if (code == ResultStatusEnum.SUCCESS.getCode()) {
                    builder.up()
                            .withDetail("app", APP)
                            .withDetail("url", jobAdminHttpSource.getUrl())
                            .withDetail("message", "ok");
                } else {
                    builder.down()
                            .withDetail("app", APP)
                            .withDetail("message", body.get("msg") + ", status code " + code);
                }
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
