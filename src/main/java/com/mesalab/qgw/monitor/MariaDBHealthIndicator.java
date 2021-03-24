package com.mesalab.qgw.monitor;

import com.jfinal.plugin.activerecord.Db;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
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

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        try {
            List<Object> query = Db.query("select 1");
            if (!CollectionUtils.isEmpty(query) && 1 == Long.parseLong(String.valueOf(query.get(0)))) {
                builder.up()
                        .withDetail("app", "MariaDB")
                        .withDetail("url", url)
                        .withDetail("message", "ok");
            } else {
                builder.down()
                        .withDetail("app", "MariaDB")
                        .withDetail("url", url)
                        .withDetail("message", "error, " + query);
            }
        } catch (RuntimeException e) {
            builder.down()
                    .withDetail("app", "MariaDB")
                    .withDetail("url", url)
                    .withDetail("message", "error, " + e.getMessage());
        }
    }
}
