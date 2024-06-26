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

    ThreadPoolTaskExecutor lightWeightThreadPool;
    ThreadPoolTaskExecutor heavyResourceThreadPool;
    ThreadPoolTaskExecutor taskThreadPool;

    private final Runnable monitor = () -> {
        try {
            Metrics.gauge("light.weight.thread.pool.core.size", TAG, lightWeightThreadPool, ThreadPoolTaskExecutor::getCorePoolSize);
            Metrics.gauge("light.weight.thread.pool.largest.size", TAG, lightWeightThreadPool, e -> e.getThreadPoolExecutor().getLargestPoolSize());
            Metrics.gauge("light.weight.thread.pool.max.size", TAG, lightWeightThreadPool, ThreadPoolTaskExecutor::getMaxPoolSize);
            Metrics.gauge("light.weight.thread.pool.active.size", TAG, lightWeightThreadPool, ThreadPoolTaskExecutor::getActiveCount);
            Metrics.gauge("light.weight.thread.pool.thread.count", TAG, lightWeightThreadPool, ThreadPoolTaskExecutor::getPoolSize);
            Metrics.gauge("light.weight.thread.pool.queue.size", TAG, lightWeightThreadPool, e -> e.getThreadPoolExecutor().getQueue().size());

            Metrics.gauge("heavy.resource.thread.pool.core.size", TAG, heavyResourceThreadPool, ThreadPoolTaskExecutor::getCorePoolSize);
            Metrics.gauge("heavy.resource.thread.pool.largest.size", TAG, heavyResourceThreadPool, e -> e.getThreadPoolExecutor().getLargestPoolSize());
            Metrics.gauge("heavy.resource.thread.pool.max.size", TAG, heavyResourceThreadPool, ThreadPoolTaskExecutor::getMaxPoolSize);
            Metrics.gauge("heavy.resource.thread.pool.active.size", TAG, heavyResourceThreadPool, ThreadPoolTaskExecutor::getActiveCount);
            Metrics.gauge("heavy.resource.thread.pool.thread.count", TAG, heavyResourceThreadPool, ThreadPoolTaskExecutor::getPoolSize);
            Metrics.gauge("heavy.resource.thread.pool.queue.size", TAG, heavyResourceThreadPool, e -> e.getThreadPoolExecutor().getQueue().size());

            Metrics.gauge("task.thread.pool.core.size", TAG, taskThreadPool, ThreadPoolTaskExecutor::getCorePoolSize);
            Metrics.gauge("task.thread.pool.largest.size", TAG, taskThreadPool, e -> e.getThreadPoolExecutor().getLargestPoolSize());
            Metrics.gauge("task.thread.pool.max.size", TAG, taskThreadPool, ThreadPoolTaskExecutor::getMaxPoolSize);
            Metrics.gauge("task.thread.pool.active.size", TAG, taskThreadPool, ThreadPoolTaskExecutor::getActiveCount);
            Metrics.gauge("task.thread.pool.thread.count", TAG, taskThreadPool, ThreadPoolTaskExecutor::getPoolSize);
            Metrics.gauge("task.thread.pool.queue.size", TAG, taskThreadPool, e -> e.getThreadPoolExecutor().getQueue().size());
        } catch (RuntimeException e) {
            log.error("Collect thread pool status error:", e.getMessage());
        }
    };

    @Override
    public void afterPropertiesSet() {
        scheduledExecutor.scheduleWithFixedDelay(monitor, 0, 10, TimeUnit.SECONDS);
    }

    public void clearQueue() {
        lightWeightThreadPool.getThreadPoolExecutor().getQueue().clear();
        heavyResourceThreadPool.getThreadPoolExecutor().getQueue().clear();
        taskThreadPool.getThreadPoolExecutor().getQueue().clear();
    }

    @Autowired
    private void setLightWeightThreadPool(ThreadPoolTaskExecutor lightWeightThreadPool) {
        this.lightWeightThreadPool = lightWeightThreadPool;
    }

    @Autowired
    private void setHeavyResourceThreadPool(ThreadPoolTaskExecutor heavyResourceThreadPool) {
        this.heavyResourceThreadPool = heavyResourceThreadPool;
    }

    @Autowired
    private void setTaskThreadPool(ThreadPoolTaskExecutor taskThreadPool) {
        this.taskThreadPool = taskThreadPool;
    }
}
