package com.mesalab.services.configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * TODO
 *
 * @Classname ThreadPoolCfgSubJob
 * @Date 2021/12/3 9:13 上午
 * @Author wWei
 */
@Configuration
@EnableAsync
public class TaskThreadPoolCfg {
    @Autowired
    TaskCfg taskCfg;
    private static final int keepAliveTime = 10;
    private static final String threadNamePrefix = "Async(sub)-Service-";

    @Bean("taskExecutor")
    public ThreadPoolTaskExecutor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(taskCfg.getPoolCorePoolSize());
        executor.setMaxPoolSize(taskCfg.getPoolMaxPoolSize());
        executor.setQueueCapacity(taskCfg.getPoolQueueCapacity());
        executor.setKeepAliveSeconds(keepAliveTime);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
