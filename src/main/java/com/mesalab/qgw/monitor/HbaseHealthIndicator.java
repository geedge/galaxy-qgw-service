package com.mesalab.qgw.monitor;

import com.mesalab.common.configuration.HBaseProperties;
import com.mesalab.common.utils.ReportCacheUtils;
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
    private HBaseProperties hBaseProperties;

    @Override
    protected void doHealthCheck(Health.Builder builder) {

        try {
            Connection connection = ReportCacheUtils.createConnection();
            boolean connectionSuccess;
            Admin admin = connection.getAdmin();
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
