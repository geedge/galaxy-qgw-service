package com.mesalab.qgw.monitor;

import cn.hutool.core.util.StrUtil;
import com.mesalab.common.enums.HttpStatusCodeEnum;
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

import java.util.Map;
import java.util.Optional;


@Component("XXL-JOB")
public class JobAdminHealthIndicator extends AbstractHealthIndicator {


    public RestTemplate restTemplate = new RestTemplate();
    @Autowired
    private JobAdminHttpSource jobAdminHttpSource;

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        if (StrUtil.contains(jobAdminHttpSource.getUrl(), "127.0.0.1")) {
            builder.up()
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
            if (responseEntity.getStatusCodeValue() == HttpStatusCodeEnum.SUCCESS.getCode()) {
                Map<String, Object> body = Optional.ofNullable(responseEntity.getBody())
                        .map(b -> (Map<String, Object>) b)
                        .orElseThrow(() -> new IllegalArgumentException("Response body is null or not a Map"));
                int code = Integer.parseInt(Optional.ofNullable(body.get("code"))
                        .map(Object::toString)
                        .orElseThrow(() -> new IllegalArgumentException("Code is missing in the response body")));
                if (code == HttpStatusCodeEnum.SUCCESS.getCode()) {
                    builder.up()
                            .withDetail("url", jobAdminHttpSource.getUrl())
                            .withDetail("message", "ok");
                } else {
                    builder.down()
                            .withDetail("message", body.get("msg") + ", status code " + code);
                }
            } else {
                builder.down()
                        .withDetail("message", "error, status code " + responseEntity.getStatusCodeValue());
            }
        } catch (RuntimeException e) {
            builder.down()
                    .withDetail("message", e.getMessage());

        }
    }
}
