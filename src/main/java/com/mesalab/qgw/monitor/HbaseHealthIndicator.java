package com.mesalab.qgw.monitor;


import cn.hutool.core.util.StrUtil;
import com.mesalab.qgw.model.api.HBaseAPISource;
import org.apache.phoenix.jdbc.PhoenixResultSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.*;


@Component
public class HbaseHealthIndicator extends AbstractHealthIndicator {
    @Autowired
    private HBaseAPISource hBaseAPISource;
    private final static String APP = "Hbase";

    static {
        try {
            Class.forName("org.apache.phoenix.jdbc.PhoenixDriver");
        } catch (
                ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        if (StrUtil.contains(hBaseAPISource.getZookeeperQuorum(), "127.0.0.1")) {
            builder.up()
                    .withDetail("app", APP)
                    .withDetail("message", "DISABLED");
            return;
        }
        Properties properties = new Properties();
        properties.setProperty("phoenix.schema.isNamespaceMappingEnabled", "true");
        properties.setProperty("phoenix.schema.mapSystemTablesToNamespace", "true");
        properties.setProperty("phoenix.query.timeoutMs", hBaseAPISource.getRpcTimeout());
        properties.setProperty("zookeeper.recovery.retry.intervalmill", "200");
        properties.setProperty("zookeeper.recovery.retry", "3");
        properties.setProperty("hbase.client.retries.number", "3");
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            String url = "jdbc:phoenix:".concat(hBaseAPISource.getZookeeperQuorum()).concat(":").concat(hBaseAPISource.getZookeeperZnodeParent());
            conn = DriverManager.getConnection(url, properties);
            String sql = "select 1";
            ps = conn.prepareStatement(sql);
            PhoenixResultSet resultSet = (PhoenixResultSet) ps.executeQuery();
            if (resultSet.next()) {
                builder.up()
                        .withDetail("app", APP)
                        .withDetail("url", hBaseAPISource.getZookeeperQuorum())
                        .withDetail("message", "ok");
            } else {
                builder.down()
                        .withDetail("app", APP)
                        .withDetail("message", "error");
            }
        } catch (RuntimeException e) {
            builder.down()
                    .withDetail("app", APP)
                    .withDetail("message", e.getMessage());
        } finally {
            if (conn != null) {
                conn.close();
            }
        }
    }
}
