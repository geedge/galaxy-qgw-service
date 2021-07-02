package com.mesalab.qgw.monitor;

import cn.hutool.core.util.StrUtil;
import com.mesalab.qgw.model.api.HBaseAPISource;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

import java.io.IOException;


@Component
public class HbaseHealthIndicator extends AbstractHealthIndicator {

    @Autowired
    private HBaseAPISource hBaseProperties;
    @Autowired
    private Connection hbaseConnection;
    private final static String object = "Hbase";

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        if (StrUtil.contains(hBaseProperties.getZookeeperQuorum(), "127.0.0.1")) {
            builder.up()
                    .withDetail("app", object)
                    .withDetail("message", "NOT REQUIRED");
            return;
        }
        try {
            boolean connectionSuccess;
            Admin admin = hbaseConnection.getAdmin();
            connectionSuccess = admin.tableExists(TableName.valueOf((hBaseProperties.getDbName() + ":" + hBaseProperties.getTableName())));

            if (connectionSuccess) {
                builder.up()
                        .withDetail("app", object)
                        .withDetail("url", hBaseProperties.getZookeeperQuorum())
                        .withDetail("message", "ok");
            } else {
                builder.down()
                        .withDetail("app", object)
                        .withDetail("message", "error");


            }
        } catch (IOException | RuntimeException e) {
            builder.down()
                    .withDetail("app", object)
                    .withDetail("message", e.getMessage());
        }

    }
}
