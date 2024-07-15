package com.mesalab.qgw.monitor;

import cn.hutool.core.util.StrUtil;
import com.alibaba.nacos.api.config.annotation.NacosValue;
import com.google.common.base.Splitter;
import com.jfinal.plugin.activerecord.Db;
import lombok.Data;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;


/**
 * @author wangwei
 * @description:
 * @date 2020/10/23 11:32 上午
 */
@Component
public class MariaDBHealthIndicator extends AbstractHealthIndicator {

    @NacosValue("${spring.datasource.url}")
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
            List<Object> query = Db.query("SELECT CURRENT_TIMESTAMP AS timestamp,version() AS version");
            if (!CollectionUtils.isEmpty(query)) {
                String substring = url.substring(url.indexOf("?") + 1);
                Map<String, String> map = Splitter.on("&").withKeyValueSeparator("=").split(substring);
                Object[] row = (Object[]) query.get(0);
                String timeZone = map.get("serverTimezone");
                builder.up()
                        .withDetail("app", APP)
                        .withDetail("url", url)
                        .withDetail("message", "ok")
                        .withDetail("version",StrUtil.subBefore(String.valueOf(row[1]), "-", false))
                        .withDetail("timeZone", timeZone)
                        .withDetail("timestamp", row[0]);

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
