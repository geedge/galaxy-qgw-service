package com.mesalab.qgw.monitor;

import com.mesalab.qgw.model.api.HBaseAPISource;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;


@Component
public class HbaseHealthIndicator extends AbstractHealthIndicator {

    @Autowired
    private HBaseAPISource hBaseProperties;
    @Autowired
    private Connection hbaseConnection;

    @Override
    protected void doHealthCheck(Health.Builder builder) {

        try {
            boolean connectionSuccess;
            Admin admin = hbaseConnection.getAdmin();
            connectionSuccess = admin.tableExists(TableName.valueOf((hBaseProperties.getDbName() + ":" + hBaseProperties.getTableName())));

            if (connectionSuccess) {
                builder.up()
                        .withDetail("app", "Hbase")
                        .withDetail("url", hBaseProperties.getZookeeperQuorum() + ":" + hBaseProperties.getZookeeperPropertyClientPort())
                        .withDetail("message", "ok");
            } else {
                builder.down()
                        .withDetail("app", "Hbase")
                        .withDetail("message", "error");


            }
        } catch (Exception e) {
            builder.down()
                    .withDetail("app", "Hbase")
                    .withDetail("message", e.getMessage());
        }

    }
}
