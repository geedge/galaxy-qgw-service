package com.mesalab.qgw.monitor;

import com.geedgenetworks.utils.DateUtils;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

import java.util.TimeZone;

/**
 * TODO
 *
 * @Classname JVMHealthIndicator
 * @Date 2022/2/17 10:41 上午
 * @Author wWei
 */
@Component
public class JVMHealthIndicator extends AbstractHealthIndicator {
    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        builder.up()
                .withDetail("app", "JVM")
                .withDetail("timeZone", TimeZone.getDefault().getID())
                .withDetail("timestamp", DateUtils.getCurrentDate("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
    }
}
