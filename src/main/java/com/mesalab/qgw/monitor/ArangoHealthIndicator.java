package com.mesalab.qgw.monitor;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.mesalab.knowledge.common.config.ArangoConfig;
import com.mesalab.knowledge.common.utils.Constant;
import com.mesalab.knowledge.common.utils.JwtCache;
import com.mesalab.qgw.service.impl.HttpClientService;
import com.geedgenetworks.utils.StringUtil;
import org.apache.http.message.BasicHeader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;


@Component
public class ArangoHealthIndicator extends AbstractHealthIndicator {

    @Autowired
    private ArangoConfig arangoConfig;
    @Autowired
    HttpClientService httpClientService;
    private final static String APP = "Arango";

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        if (StrUtil.contains(arangoConfig.getJwturl(), "127.0.0.1")) {
            builder.up()
                    .withDetail("app", APP)
                    .withDetail("message", "DISABLED");
            return;
        }
        Map<String, String> params = new HashMap<>();
        try {
            Object jwt = JwtCache.get(Constant.ARANGO_CACHE_JWT);
            if (StringUtil.isEmpty(jwt)) {
                params.put("username", arangoConfig.getUsername());
                params.put("password", arangoConfig.getPin());
                String res = httpClientService.httpPost(arangoConfig.getJwturl(), JSON.toJSONString(params));
                Map resMap = JSON.parseObject(res, Map.class);
                if (StringUtil.isEmpty(resMap) || StringUtil.isEmpty(resMap.get(Constant.JWT))) {
                    builder.down()
                            .withDetail("app", APP)
                            .withDetail("message", "获取Arango jwt 失败: " + arangoConfig.getServer());
                    return;
                } else {
                    jwt = Constant.ARANGO_JWT_BEARER + resMap.get(Constant.JWT);
                    JwtCache.put(Constant.ARANGO_CACHE_JWT, jwt);
                }
            }
            String result = httpClientService.httpPost(arangoConfig.getServer() + "/_db/" + arangoConfig.getDatabase() + "/_api/version",
                    StringUtil.EMPTY, new BasicHeader(HttpHeaders.AUTHORIZATION, String.valueOf(jwt)));
            Map map = JSON.parseObject(result, Map.class);
            if (StringUtil.isEmpty(map) || StringUtil.isEmpty(map.get("version"))) {
                builder.down()
                        .withDetail("app", APP)
                        .withDetail("message", "连接Arango 失败: " + result);
            } else {
                builder.up()
                        .withDetail("app", APP)
                        .withDetail("url", arangoConfig.getServer())
                        .withDetail("message", "ok");
            }
        } catch (RuntimeException e) {
            builder.down()
                    .withDetail("app", APP)
                    .withDetail("message", "连接Arango 失败");
        }
    }
}
