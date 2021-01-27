package com.mesalab.qgw.monitor;
import cn.hutool.core.util.URLUtil;
import com.google.common.collect.Maps;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.qgw.model.api.DruidIoHttpSource;
import lombok.Data;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class DruidHealthIndicator extends AbstractHealthIndicator {


    public RestTemplate restTemplate = new RestTemplate();
    @Autowired
    private DruidIoHttpSource druidIoHttpSource;

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        String queryURL = URLUtil.normalize(druidIoHttpSource.getUrl());
        DruidHealthIndicator.DruidQueryParam druidQueryParam = new DruidHealthIndicator.DruidQueryParam();

        druidQueryParam.setQuery("select count(*) from sys.servers ");
        druidQueryParam.getContext().put("skipEmptyBuckets", druidIoHttpSource.getSkipEmptyBuckets());
        druidQueryParam.setResultFormat("object");
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity request = new HttpEntity(JsonMapper.toJsonString(druidQueryParam), headers);
            ResponseEntity responseEntity  = restTemplate.postForEntity(queryURL, request, Object.class);

            if (responseEntity.getStatusCodeValue() == HttpStatus.SC_OK) {
                builder.up()
                        .withDetail("app", "Druid")
                        .withDetail("url", druidIoHttpSource.getUrl())
                        .withDetail("message", "ok");
            } else {
                builder.down()
                        .withDetail("app", "Druid")
                        .withDetail("message", "error, status code " + responseEntity.getStatusCodeValue());

            }



        } catch (Exception e) {
            builder.down()
                    .withDetail("app", "Druid")
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
