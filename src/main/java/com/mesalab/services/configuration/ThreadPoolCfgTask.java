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
 * @Classname ThreadPoolTaskConfig
 * @Date 2021/12/1 9:20 上午
 * @Author wWei
 */
@Configuration
@EnableAsync
public class ThreadPoolCfgTask {
    @Autowired
    TaskCfg taskCfg;
    private static final String threadNamePrefix = "Async-Service-";

    @Bean("taskExecutor")
    public ThreadPoolTaskExecutor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(taskCfg.getTaskCorePoolSize());
        executor.setMaxPoolSize(taskCfg.getTaskMaxPoolSize());
        executor.setQueueCapacity(taskCfg.getTaskQueueCapacity());
        executor.setKeepAliveSeconds(taskCfg.getTaskKeepAliveTime());
        executor.setThreadNamePrefix(threadNamePrefix);

        // 线程池对拒绝任务的处理策略
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // 初始化
        executor.initialize();
        return executor;
    }
}