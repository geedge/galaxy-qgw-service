package com.mesalab.qgw.monitor;

import cn.hutool.core.util.StrUtil;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.qgw.service.impl.HttpClientService;
import com.mesalab.services.configuration.HosConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class HosHealthIndicator extends AbstractHealthIndicator {
    @Autowired
    private HttpClientService httpClientService;
    @Autowired
    private HosConfig hosConfig;
    private final static String APP = "Hos";

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        if (StrUtil.contains(hosConfig.getUri(),"127.0.0.1")){
            builder.up()
                    .withDetail("app", APP)
                    .withDetail("message", "DISABLED");
            return;
        }

        String url = hosConfig.getUri();
        Map<String, String> headers = new HashMap<>();
        headers.put("token", hosConfig.getToken());
        Map<String, String> resultMap = httpClientService.httpGet(url, headers, 3000);
        if (Integer.parseInt(resultMap.get("status")) == ResultStatusEnum.SUCCESS.getCode()) {
            builder.up()
                    .withDetail("app", APP)
                    .withDetail("url", url)
                    .withDetail("message", "ok");
        } else {
            builder.down()
                    .withDetail("app", APP)
                    .withDetail("message", resultMap.get("message"));
        }
    }
}
