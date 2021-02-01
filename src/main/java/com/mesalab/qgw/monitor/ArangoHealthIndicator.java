package com.mesalab.qgw.monitor;

import com.mesalab.common.configuration.HBaseProperties;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.knowledge.common.config.ArangoConfig;
import com.mesalab.knowledge.common.utils.Constant;
import com.mesalab.knowledge.common.utils.JwtCache;
import com.mesalab.knowledge.strategy.QueryProvider;
import com.mesalab.qgw.service.impl.HttpClientService;
import com.zdjizhi.utils.JsonMapper;
import com.zdjizhi.utils.StringUtil;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.http.HttpStatus;
import org.apache.http.message.BasicHeader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;


@Component
public class ArangoHealthIndicator extends AbstractHealthIndicator {

    @Autowired
    private ArangoConfig arangoConfig;
    @Autowired
    HttpClientService httpClientService;

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        Map<String, String> params = new HashMap<>();
        try {
            Object jwt = JwtCache.get(Constant.ARANGO_CACHE_JWT);
            if (StringUtil.isEmpty(jwt)) {
                params.put("username", arangoConfig.getUsername());
                params.put("password", arangoConfig.getPassword());
                String res = httpClientService.httpPost(arangoConfig.getJwturl(), JsonMapper.toJsonString(params));
                Map resMap = (Map) JsonMapper.fromJsonString(res, Map.class);
                if (StringUtil.isEmpty(resMap) || StringUtil.isEmpty(resMap.get(Constant.JWT))) {
                    builder.down()
                            .withDetail("app", "Arango")
                            .withDetail("message", "获取Arango jwt 失败: " + arangoConfig.getServer());
                    return;
                } else {
                    jwt = Constant.ARANGO_JWT_BEARER + resMap.get(Constant.JWT);
                    JwtCache.put(Constant.ARANGO_CACHE_JWT, jwt);
                }
            }
            String result = httpClientService.httpPost(arangoConfig.getServer() + "/_db/" + arangoConfig.getDatabase() + "/_api/version",
                                            StringUtil.EMPTY, new BasicHeader(HttpHeaders.AUTHORIZATION, String.valueOf(jwt)));
            Map map = (Map) JsonMapper.fromJsonString(result, Map.class);
            if (StringUtil.isEmpty(map) || StringUtil.isEmpty(map.get("version"))) {
                builder.down()
                        .withDetail("app", "Arango")
                        .withDetail("message", "连接Arango 失败: " + result);
            } else {
                builder.up()
                        .withDetail("app", "Arango")
                        .withDetail("url", arangoConfig.getServer())
                        .withDetail("message", "ok");
            }
        } catch (Exception e) {
            builder.down()
                    .withDetail("app", "Arango")
                    .withDetail("message", "连接Arango 失败");
        }
    }
}
