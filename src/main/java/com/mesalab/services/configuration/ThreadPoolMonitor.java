package com.mesalab.services.configuration;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.*;


/**
 * @author wangwei
 */
@Service
public class ThreadPoolMonitor implements InitializingBean {

    private static final Log log = LogFactory.get();
    private static final String EXECUTOR_NAME = "ThreadPoolMonitorSample";
    private static final Iterable<Tag> TAG = Collections.singletonList(Tag.of("Async", EXECUTOR_NAME));
    private final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();

    @Autowired
    ThreadPoolTaskExecutor jobExecutor;
    @Autowired
    ThreadPoolTaskExecutor taskExecutor;

    private final Runnable monitor = () -> {
        try {
            Metrics.gauge("job.thread.pool.core.size", TAG, jobExecutor, ThreadPoolTaskExecutor::getCorePoolSize);
            Metrics.gauge("job.thread.pool.largest.size", TAG, jobExecutor, e -> e.getThreadPoolExecutor().getLargestPoolSize());
            Metrics.gauge("job.thread.pool.max.size", TAG, jobExecutor, ThreadPoolTaskExecutor::getMaxPoolSize);
            Metrics.gauge("job.thread.pool.active.size", TAG, jobExecutor, ThreadPoolTaskExecutor::getActiveCount);
            Metrics.gauge("job.thread.pool.thread.count", TAG, jobExecutor, ThreadPoolTaskExecutor::getPoolSize);
            Metrics.gauge("job.thread.pool.queue.size", TAG, jobExecutor, e -> e.getThreadPoolExecutor().getQueue().size());

            Metrics.gauge("task.thread.pool.core.size", TAG, taskExecutor, ThreadPoolTaskExecutor::getCorePoolSize);
            Metrics.gauge("task.thread.pool.largest.size", TAG, taskExecutor, e -> e.getThreadPoolExecutor().getLargestPoolSize());
            Metrics.gauge("task.thread.pool.max.size", TAG, taskExecutor, ThreadPoolTaskExecutor::getMaxPoolSize);
            Metrics.gauge("task.thread.pool.active.size", TAG, taskExecutor, ThreadPoolTaskExecutor::getActiveCount);
            Metrics.gauge("task.thread.pool.thread.count", TAG, taskExecutor, ThreadPoolTaskExecutor::getPoolSize);
            Metrics.gauge("task.thread.pool.queue.size", TAG, taskExecutor, e -> e.getThreadPoolExecutor().getQueue().size());
        } catch (RuntimeException e) {
            log.error("Collect thread pool status error:", e.getMessage());
        }
    };

    @Override
    public void afterPropertiesSet() {
        scheduledExecutor.scheduleWithFixedDelay(monitor, 0, 10, TimeUnit.SECONDS);
    }

    public void clearQueue() {
        jobExecutor.getThreadPoolExecutor().getQueue().clear();
        taskExecutor.getThreadPoolExecutor().getQueue().clear();
    }
}
