package com.mesalab.qgw.monitor;

import cn.hutool.core.util.StrUtil;
import com.mesalab.common.enums.HttpStatusCodeEnum;
import com.mesalab.qgw.service.impl.HttpClientService;
import com.mesalab.services.configuration.HosConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component("HOS")
public class HosHealthIndicator extends AbstractHealthIndicator {
    @Autowired
    private HttpClientService httpClientService;
    @Autowired
    private HosConfig hosConfig;

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        if (StrUtil.contains(hosConfig.getUri(),"127.0.0.1")){
            builder.up()
                    .withDetail("message", "DISABLED");
            return;
        }

        String url = hosConfig.getUri();
        Map<String, String> headers = new HashMap<>();
        headers.put("token", hosConfig.getToken());
        Map<String, String> resultMap = httpClientService.httpGet(url, headers, 3000);
        if (Integer.parseInt(resultMap.get("status")) == HttpStatusCodeEnum.SUCCESS.getCode()) {
            builder.up()
                    .withDetail("url", url)
                    .withDetail("message", "ok");
        } else {
            builder.down()
                    .withDetail("message", resultMap.get("message"));
        }
    }
}
