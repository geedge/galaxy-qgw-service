package com.mesalab.qgw.monitor;


import cn.hutool.core.util.StrUtil;
import com.mesalab.qgw.dialect.HbaseDialect;
import com.mesalab.qgw.model.basic.HBaseAPISource;
import org.apache.phoenix.jdbc.PhoenixResultSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

import java.sql.*;


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
        HbaseDialect.PROPERTIES.setProperty("hbase.rpc.timeout", hBaseAPISource.getRpcTimeout());
        HbaseDialect.PROPERTIES.setProperty("hbase.client.scanner.timeout.period", hBaseAPISource.getRpcTimeout());
        HbaseDialect.PROPERTIES.setProperty("phoenix.query.timeoutMs", hBaseAPISource.getRpcTimeout());
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            String url = "jdbc:phoenix:".concat(hBaseAPISource.getZookeeperQuorum()).concat(":").concat(hBaseAPISource.getZookeeperZnodeParent());
            conn = DriverManager.getConnection(url, HbaseDialect.PROPERTIES);
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
