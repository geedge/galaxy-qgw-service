package com.mesalab.common.configuration;

import com.mesalab.services.configuration.JobConfig;
import com.mesalab.services.configuration.TaskConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * TODO
 *
 * @Classname TaskThreadPoolConfig
 * @Date 2024/6/26 10:56
 * @Author wWei
 */
@Configuration
@EnableAsync
public class TaskThreadPoolConfiguration {
    private TaskConfig taskConfig;
    private static final String THREAD_NAME_PREFIX = "Task-ThreadPool-";

    @Bean("taskThreadPool")
    public ThreadPoolTaskExecutor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(taskConfig.getPoolCorePoolSize());
        executor.setMaxPoolSize(taskConfig.getPoolMaxPoolSize());
        executor.setQueueCapacity(taskConfig.getPoolQueueCapacity());
        executor.setKeepAliveSeconds(10);
        executor.setThreadNamePrefix(THREAD_NAME_PREFIX);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }

    @Autowired
    private void setTaskConfig(TaskConfig taskConfig) {
        this.taskConfig = taskConfig;
    }
}
