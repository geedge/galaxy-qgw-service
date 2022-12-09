package com.mesalab.qgw.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * TODO
 *
 * @Classname ThreadPoolTaskConfig
 * @Date 2021/12/1 9:20 上午
 * @Author wWei
 */
@Configuration
@EnableAsync
public class JobThreadPoolCfg {
    @Autowired
    JobConfig jobConfig;
    private static final int keepAliveTime = 10;
    private static final String threadNamePrefix = "Async-Service-";

    @Bean("jobExecutor")
    public ThreadPoolTaskExecutor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(jobConfig.getPoolCorePoolSize());
        executor.setMaxPoolSize(jobConfig.getPoolMaxPoolSize());
        executor.setQueueCapacity(jobConfig.getPoolQueueCapacity());
        executor.setKeepAliveSeconds(keepAliveTime);
        executor.setThreadNamePrefix(threadNamePrefix);

        // 线程池对拒绝任务的处理策略
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // 初始化
        executor.initialize();
        return executor;
    }
}