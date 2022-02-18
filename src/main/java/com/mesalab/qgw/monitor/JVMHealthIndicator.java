package com.mesalab.qgw.monitor;

import cn.hutool.core.date.DateUtil;
import com.mesalab.common.utils.TimeZoneUtil;
import com.zdjizhi.utils.DateUtils;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.xml.crypto.Data;
import java.text.SimpleDateFormat;
import java.util.Date;

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
                .withDetail("timeZone", TimeZoneUtil.timeZoneTransition(System.getProperty("user.timezone")))
                .withDetail("curTimestamp", DateUtils.getCurrentDate("yyyy-MM-dd'T'HH:mm:ss:SSS'Z'"));

    }
}
