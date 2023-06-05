package com.mesalab.qgw.monitor;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.google.common.collect.Maps;
import com.mesalab.qgw.model.api.ElasticsearchHttpSource;
import lombok.Data;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    @Autowired
    private RestClient restClient;
    private final static String APP = "Elasticsearch";

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        if (StrUtil.contains(elasticsearchHttpSource.getUrl(), "127.0.0.1")) {
            builder.up()
                    .withDetail("app", APP)
                    .withDetail("message", "DISABLED");
            return;
        }
        Request request = new Request("POST", "/_sql?format=json");
        HashMap<String, Object> queryParam = Maps.newHashMap();
        queryParam.put("query", "select format(now(),'Z') as timeZone, now() as curTimestamp");
        request.setJsonEntity(JSON.toJSONString(queryParam));
        Response response;
        try {
            response = restClient.performRequest(request);
            String responseBody = EntityUtils.toString(response.getEntity());
            ResponseEntity<String> responseEntity = new ResponseEntity<>(responseBody, org.springframework.http.HttpStatus.OK);
            Map<String, Object> body = JSON.parseObject(responseEntity.getBody(), Map.class);
            String timeZone = ((List) ((List) body.get("rows")).get(0)).get(0).toString();
            String curTimestamp = ((List) ((List) body.get("rows")).get(0)).get(1).toString();
            builder.up()
                    .withDetail("app", APP)
                    .withDetail("url", elasticsearchHttpSource.getUrl())
                    .withDetail("message", "ok")
                    .withDetail("timeZone", timeZone)
                    .withDetail("curTimestamp", curTimestamp);
        } catch (RuntimeException e) {
            builder.down()
                    .withDetail("app", APP)
                    .withDetail("message", e.getMessage());
        }
    }

    @Data
    class QueryParam {
        private String query;
    }

}
