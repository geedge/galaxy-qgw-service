package com.mesalab.qgw.monitor;

import cn.hutool.core.util.StrUtil;
import com.mesalab.qgw.config.ArangoConfig;
import com.mesalab.qgw.constant.ArangoConstant;
import com.mesalab.qgw.utils.HttpClientService;
import com.mesalab.qgw.utils.JwtCache;
import com.zdjizhi.utils.JsonMapper;
import com.zdjizhi.utils.StringUtil;
import org.apache.http.message.BasicHeader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import static com.zdjizhi.calcite.linq4j.tree.ExpressionType.Constant;


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
            Object jwt = JwtCache.get(ArangoConstant.ARANGO_CACHE_JWT);
            if (StringUtil.isEmpty(jwt)) {
                params.put("username", arangoConfig.getUsername());
                params.put("password", arangoConfig.getPin());
                String res = httpClientService.httpPost(arangoConfig.getJwturl(), JsonMapper.toJsonString(params));
                Map resMap = (Map) JsonMapper.fromJsonString(res, Map.class);
                if (StringUtil.isEmpty(resMap) || StringUtil.isEmpty(resMap.get(ArangoConstant.JWT))) {
                    builder.down()
                            .withDetail("app", APP)
                            .withDetail("message", "获取Arango jwt 失败: " + arangoConfig.getServer());
                    return;
                } else {
                    jwt = ArangoConstant.ARANGO_JWT_BEARER + resMap.get(ArangoConstant.JWT);
                    JwtCache.put(ArangoConstant.ARANGO_CACHE_JWT, jwt);
                }
            }
            String result = httpClientService.httpPost(arangoConfig.getServer() + "/_db/" + arangoConfig.getDatabase() + "/_api/version",
                    StringUtil.EMPTY, new BasicHeader(HttpHeaders.AUTHORIZATION, String.valueOf(jwt)));
            Map map = (Map) JsonMapper.fromJsonString(result, Map.class);
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
