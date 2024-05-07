package com.mesalab.common.configuration;

import com.mesalab.services.configuration.JobConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * TODO
 *
 * @Classname lightWeightThreadPoolConfiguration
 * @Date 2021/12/1 9:20 上午
 * @Author wWei
 */
@Configuration
@EnableAsync
public class LightWeightThreadPoolConfiguration {

    private JobConfig jobConfig;
    private static final String THREAD_NAME_PREFIX = "Light-Weight-ThreadPool";

    @Bean("lightWeightThreadPool")
    public ThreadPoolTaskExecutor getLightWeightThreadPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(jobConfig.getLightWeightCorePoolSize());
        executor.setMaxPoolSize(jobConfig.getLightWeightMaxPoolSize());
        executor.setQueueCapacity(jobConfig.getLightWeightQueueCapacity());
        executor.setKeepAliveSeconds(10);
        executor.setThreadNamePrefix(THREAD_NAME_PREFIX);

        // 线程池对拒绝任务的处理策略
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // 初始化
        executor.initialize();
        return executor;
    }

    @Autowired
    private void setJobConfig(JobConfig jobConfig) {
        this.jobConfig = jobConfig;
    }
}