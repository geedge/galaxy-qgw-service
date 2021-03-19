package com.mesalab.common.configuration;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.mesalab.qgw.model.api.HBaseAPISource;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;


@Configuration
public class HBaseConfiguration {
    private static final Log log = LogFactory.get();
    @Autowired
    private HBaseAPISource hBaseAPISource;


    @Bean(name = "hbaseConnection")
    public Connection getConnection() {
        org.apache.hadoop.conf.Configuration conf = null;
        Connection con = null;
        try {
            if (conf == null) {
                conf = org.apache.hadoop.hbase.HBaseConfiguration.create();
                conf.set("hbase.zookeeper.quorum", hBaseAPISource.getZookeeperQuorum());
                conf.set("hbase.zookeeper.znode.parent", hBaseAPISource.getZookeeperZnodeParent());
                conf.set("hbase.rpc.timeout", hBaseAPISource.getRpcTimeout());
                conf.set("hbase.client.ipc.pool.size", hBaseAPISource.getClientIpcPoolSize());
                conf.set("hbase.client.ipc.pool.type", "ThreadLocalPool");
                conf.set("zookeeper.recovery.retry.intervalmill", "200");
                conf.set("zookeeper.recovery.retry", "3");
                conf.set("hbase.client.retries.number", "3");
            }
            con = ConnectionFactory.createConnection(conf);
        } catch (IOException e) {
            log.error("create connection error: ", e);
        }
        return con;
    }
}
