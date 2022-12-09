package com.mesalab.qgw.config;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author wxs
 * @Date 2022/9/5
 */
@Configuration
public class ZkConfiguration {

    //zookeeper的地址
    @Value("${hbase.zookeeperQuorum}")
    private String zookeeperServer;

    CuratorFramework client = null;


    @Bean(name = "client")
    public CuratorFramework getConfigService() {
        // 重试策略,等待10s,最大重试10次
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(10000,10);
        client = CuratorFrameworkFactory.builder()
                .connectString(zookeeperServer)
                .sessionTimeoutMs(5000)
                .connectionTimeoutMs(5000)
                .retryPolicy(retryPolicy)
                .build();
        client.start();
        return client;
    }

}
