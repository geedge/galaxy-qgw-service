package com.mesalab.qgw.monitor;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mesalab.common.enums.HttpStatusCodeEnum;
import com.mesalab.qgw.model.basic.StarRocksHttpSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.*;

/**
 * @author wangwei
 */
@Component("StarRocks")
public class StarRocksHealthIndicator extends AbstractHealthIndicator {
    public RestTemplate restTemplate = new RestTemplate();
    private static final Log log = LogFactory.get();
    private StarRocksHttpSource starRocksHttpSource;

    @Override
    protected void doHealthCheck(Health.Builder builder) throws IOException {

        if (StrUtil.contains(starRocksHttpSource.getUrl(), "127.0.0.1")) {
            builder.up()
                    .withDetail("message", "DISABLED");
            return;
        }
        String queryURL = URLUtil.normalize(starRocksHttpSource.getUrl() + "/api/v1/catalogs/default_catalog/databases/" + starRocksHttpSource.getDbname() + "/sql");
        Map<String, Object> body = Maps.newHashMap();
        body.put("query", "select @@global.time_zone as timeZone, to_iso8601(now()) as curTimestamp");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBasicAuth(starRocksHttpSource.getUsername(), starRocksHttpSource.getPin());
        HttpEntity<String> request = new HttpEntity<>(JSON.toJSONString(body), headers);
        ResponseEntity<String> responseEntity = restTemplate.postForEntity(queryURL, request, String.class);
        if (responseEntity.getStatusCodeValue() == HttpStatusCodeEnum.SUCCESS.getCode()) {
            if (responseEntity.getBody() != null) {
                String responseBody = responseEntity.getBody();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(new java.io.ByteArrayInputStream(responseBody.getBytes()), Charset.defaultCharset()))) {
                    Map<String, Object> result = parseNDJSON(reader);
                    List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("data");
                    Map<String, Object> firstElement = data.get(0);
                    Object timeZone = firstElement.get("timeZone");
                    Object curDatetime = firstElement.get("curTimestamp");
                    builder.up()
                            .withDetail("url", starRocksHttpSource.getUrl())
                            .withDetail("message", "ok")
                            .withDetail("timeZone", timeZone)
                            .withDetail("timestamp", curDatetime);
                } catch (IOException | RuntimeException e) {
                    builder.down()
                            .withDetail("message", e.getMessage());
                }
            } else {
                builder.down()
                        .withDetail("message", "Failed to fetch data: " + responseEntity.getStatusCode());
            }
        } else {
            builder.down()
                    .withDetail("message", "Failed to fetch data: " + responseEntity.getStatusCode());
        }

    }


    private Map<String, Object> parseNDJSON(BufferedReader reader) throws IOException {
        Map<String, Object> result = Maps.newHashMap();
        List<Map<String, Object>> dataList = Lists.newArrayList();
        long count = reader.lines().count();
        reader.reset();
        if (count == 1) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    JSONObject jsonObject = JSON.parseObject(line);
                    for (String key : jsonObject.keySet()) {
                        if ("data".equals(key)) {
                            dataList.addAll((Collection<? extends Map<String, Object>>) jsonObject.get(key));
                        } else if ("meta".equals(key)) {
                            result.put("meta", jsonObject.get("meta"));
                        } else if ("statistics".equals(key)) {
                            result.put("statistics", jsonObject.get("statistics"));
                        }


                    }
                } catch (Exception e) {
                    log.error("parse NDJSON error. message: {}", e.getMessage(), e);
                    throw new RuntimeException("parse NDJSON error. message: " + e.getMessage());
                }
            }
        } else {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    JSONObject jsonObject = JSON.parseObject(line);
                    boolean isData = jsonObject.containsKey("data");
                    if (isData) {
                        List<Map<String, Object>> meta = (List<Map<String, Object>>) result.get("meta");
                        List<Object> data = (List<Object>) jsonObject.get("data");

                        Map<String, Object> dataItem = Maps.newHashMap();
                        for (int i = 0; i < data.size(); i++) {
                            dataItem.put(String.valueOf(meta.get(i).get("name")), data.get(i));
                        }
                        dataList.add(dataItem);
                        continue;
                    }
                    // 解析每一行的 JSON 数据

                    boolean isMeta = jsonObject.containsKey("meta");
                    if (isMeta) {
                        result.put("meta", jsonObject.get("meta"));
                        continue;
                    }

                    boolean isStatistics = jsonObject.containsKey("statistics");
                    if (isStatistics) {
                        result.put("statistics", jsonObject.get("statistics"));
                        continue;
                    }
                } catch (Exception e) {
                    log.error("parse NDJSON error. message: {}", e.getMessage(), e);
                    throw new RuntimeException("parse NDJSON error. message: " + e.getMessage());
                }
            }
        }
        result.put("data", dataList);
        return result;
    }

    @Autowired
    private void setStarRocksHttpSource(StarRocksHttpSource starRocksHttpSource) {
        this.starRocksHttpSource = starRocksHttpSource;
    }
}
