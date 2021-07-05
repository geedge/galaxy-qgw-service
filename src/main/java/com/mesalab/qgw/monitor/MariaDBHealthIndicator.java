package com.mesalab.qgw.monitor;

import cn.hutool.core.util.StrUtil;
import com.jfinal.plugin.activerecord.Db;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * @author wangwei
 * @description:
 * @date 2020/10/23 11:32 上午
 */
@Component
public class MariaDBHealthIndicator extends AbstractHealthIndicator {

    @Value("${spring.datasource.url}")
    private String url;
    private final static String APP = "MariaDB";

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        if (StrUtil.contains(url, "127.0.0.1")) {
            builder.up()
                    .withDetail("app", APP)
                    .withDetail("message", "DISABLED");
            return;
        }
        try {
            List<Object> query = Db.query("select 1");
            if (!CollectionUtils.isEmpty(query) && 1 == Long.parseLong(String.valueOf(query.get(0)))) {
                builder.up()
                        .withDetail("app", APP)
                        .withDetail("url", url)
                        .withDetail("message", "ok");
            } else {
                builder.down()
                        .withDetail("app", APP)
                        .withDetail("url", url)
                        .withDetail("message", "error, " + query);
            }
        } catch (RuntimeException e) {
            builder.down()
                    .withDetail("app", APP)
                    .withDetail("url", url)
                    .withDetail("message", "error, " + e.getMessage());
        }
    }
}
