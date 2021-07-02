package com.mesalab.qgw.monitor;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import com.google.common.collect.Maps;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.qgw.model.api.ElasticsearchHttpSource;
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

/**
 * TODO
 *
 * @Classname ElasticsearchHealthIndicator
 * @Date 2021/6/21 5:34 下午
 * @Author wWei
 */
@Component
public class ElasticsearchHealthIndicator extends AbstractHealthIndicator {
    public RestTemplate restTemplate = new RestTemplate();
    @Autowired
    private ElasticsearchHttpSource elasticsearchHttpSource;

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        if (StrUtil.contains(elasticsearchHttpSource.getUrl(), "127.0.0.1")) {
            builder.up()
                    .withDetail("app", "Druid")
                    .withDetail("message", "NOT REQUIRED");
            return;
        }
        String queryURL = URLUtil.normalize(elasticsearchHttpSource.getUrl() + "?format=json");
        QueryParam queryParam = new QueryParam();
        queryParam.setQuery("select database()");
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity request = new HttpEntity(JsonMapper.toJsonString(queryParam), headers);
            ResponseEntity responseEntity = restTemplate.postForEntity(queryURL, request, Object.class);

            if (responseEntity.getStatusCodeValue() == HttpStatus.SC_OK) {
                builder.up()
                        .withDetail("app", "Elasticsearch")
                        .withDetail("url", elasticsearchHttpSource.getUrl())
                        .withDetail("message", "ok");
            } else {
                builder.down()
                        .withDetail("app", "Elasticsearch")
                        .withDetail("message", "error, status code " + responseEntity.getStatusCodeValue());

            }


        } catch (RuntimeException e) {
            builder.down()
                    .withDetail("app", "Elasticsearch")
                    .withDetail("message", e.getMessage());

        }
    }

    @Data
    class QueryParam {
        private String query;
    }

}
