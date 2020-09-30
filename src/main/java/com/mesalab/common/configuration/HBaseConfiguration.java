package com.mesalab.common.configuration;

import com.mesalab.qgw.model.api.HBaseAPISource;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Slf4j
@Configuration
@EnableConfigurationProperties(HBaseProperties.class)
public class HBaseConfiguration {

    private final HBaseProperties hBaseProperties;

    @Autowired
    public HBaseConfiguration(HBaseProperties hBaseProperties) {
        this.hBaseProperties = hBaseProperties;
    }

    @Bean(name = "hBaseAPISource")
    public HBaseAPISource hBaseHttpSource() {
        HBaseAPISource apiSource = new HBaseAPISource();
        apiSource.setZookeeperQuorum(hBaseProperties.getZookeeperQuorum());
        apiSource.setZookeeperPropertyClientPort(hBaseProperties.getZookeeperPropertyClientPort());
        apiSource.setZookeeperParent(hBaseProperties.getZookeeperZnodeParent());
        apiSource.setClientIpcPoolSize(hBaseProperties.getClientIpcPoolSize());
        apiSource.setRpcTimeout(hBaseProperties.getRpcTimeout());
        apiSource.setDbName(hBaseProperties.getDbName());
        apiSource.setTableName(hBaseProperties.getTableName());
        apiSource.setColumnFamily(hBaseProperties.getColumnFamily());
        apiSource.setColumnName(hBaseProperties.getColumnName());
        return apiSource;
    }

    @Bean(name = "hbaseConnection")
    public Connection getConnection() {
        org.apache.hadoop.conf.Configuration conf = null;
        Connection con = null;
        if (conf == null) {
            conf = org.apache.hadoop.hbase.HBaseConfiguration.create();
            conf.set("hbase.zookeeper.quorum", hBaseProperties.getZookeeperQuorum());
            conf.set("hbase.zookeeper.property.clientPort", hBaseProperties.getZookeeperPropertyClientPort());
            conf.set("hbase.zookeeper.znode.parent", hBaseProperties.getZookeeperZnodeParent());
            conf.set("hbase.rpc.timeout", hBaseProperties.getRpcTimeout());
            conf.set("hbase.client.ipc.pool.size", hBaseProperties.getClientIpcPoolSize());
            conf.set("hbase.client.ipc.pool.type", "ThreadLocalPool");
            conf.set("zookeeper.recovery.retry.intervalmill", "200");
            conf.set("zookeeper.recovery.retry", "3");
            conf.set("hbase.client.retries.number", "3");
        }
        try {
            con = ConnectionFactory.createConnection(conf);
        } catch (Exception e) {
            log.error("create connection error: ", e);
            e.printStackTrace();
        }
        return con;
    }
}
