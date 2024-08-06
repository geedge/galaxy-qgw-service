package com.mesalab.qgw.monitor;


import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.mesalab.qgw.dialect.HbaseDialect;
import com.mesalab.qgw.model.basic.HBaseAPISource;
import org.apache.phoenix.jdbc.PhoenixResultSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

import java.sql.*;


@Component("ApacheHBase")
public class HbaseHealthIndicator extends AbstractHealthIndicator {
    private static final Log log = LogFactory.get();
    @Autowired
    private HBaseAPISource hBaseAPISource;

    static {
        try {
            Class.forName("org.apache.phoenix.jdbc.PhoenixDriver");
        } catch (ClassNotFoundException e) {
            log.error("get PhoenixDriver error:{}", e.getMessage());
        }
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        if (StrUtil.contains(hBaseAPISource.getZookeeperQuorum(), "127.0.0.1")) {
            builder.up()
                    .withDetail("message", "DISABLED");
            return;
        }
        HbaseDialect.PROPERTIES.setProperty("hbase.rpc.timeout", hBaseAPISource.getRpcTimeout());
        HbaseDialect.PROPERTIES.setProperty("hbase.client.scanner.timeout.period", hBaseAPISource.getRpcTimeout());
        HbaseDialect.PROPERTIES.setProperty("phoenix.query.timeoutMs", hBaseAPISource.getRpcTimeout());
        String url = "jdbc:phoenix:".concat(hBaseAPISource.getZookeeperQuorum()).concat(":").concat(hBaseAPISource.getZookeeperZnodeParent());
        String sql = "select 1";
        try (Connection conn = DriverManager.getConnection(url, HbaseDialect.PROPERTIES);
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet resultSet = ps.executeQuery()) {
            if (resultSet.next()) {
                builder.up()
                        .withDetail("url", hBaseAPISource.getZookeeperQuorum())
                        .withDetail("message", "ok");
            } else {
                builder.down()
                        .withDetail("message", "error");
            }
        } catch (RuntimeException e) {
            builder.down()
                    .withDetail("message", e.getMessage());
        }
    }
}