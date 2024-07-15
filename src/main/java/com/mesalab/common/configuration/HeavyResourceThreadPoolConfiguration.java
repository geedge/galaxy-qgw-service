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
 * @Classname HeavyResourceThreadPoolConfiguration
 * @Date 2021/12/1 9:20 上午
 * @Author wWei
 */
@Configuration
@EnableAsync
public class HeavyResourceThreadPoolConfiguration {
    private static final String THREAD_NAME_PREFIX = "Heavy-Resource-ThreadPool-";

    private JobConfig jobCfg;

    @Bean("heavyResourceThreadPool")
    public ThreadPoolTaskExecutor getHeavyResourceThreadPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(jobCfg.getHeavyResourceCorePoolSize());
        executor.setMaxPoolSize(jobCfg.getHeavyResourceMaxPoolSize());
        executor.setQueueCapacity(jobCfg.getHeavyResourceQueueCapacity());
        executor.setKeepAliveSeconds(10);
        executor.setThreadNamePrefix(THREAD_NAME_PREFIX);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }

    @Autowired
    public void setJobConfig(JobConfig jobCfg) {
        this.jobCfg = jobCfg;
    }
}