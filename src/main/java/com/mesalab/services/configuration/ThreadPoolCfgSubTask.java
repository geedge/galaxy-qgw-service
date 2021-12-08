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
public class ThreadPoolCfgSubTask {
    @Autowired
    TaskCfg taskCfg;
    private static final String threadNamePrefix = "Async(sub)-Service-";

    @Bean("subTaskExecutor")
    public ThreadPoolTaskExecutor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(taskCfg.getSubTaskCorePoolSize());
        executor.setMaxPoolSize(taskCfg.getSubTaskMaxPoolSize());
        executor.setQueueCapacity(taskCfg.getSubTaskQueueCapacity());
        executor.setKeepAliveSeconds(taskCfg.getSubTaskKeepAliveTime());
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
