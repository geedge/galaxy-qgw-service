package com.mesalab.services.configuration;

import com.alibaba.nacos.api.config.annotation.NacosValue;
import lombok.Data;
import org.springframework.stereotype.Component;

/**
 * TODO
 *
 * @Classname TaskCfg
 * @Date 2021/12/8 4:26 下午
 * @Author wWei
 */
@Data
@Component
public class JobConfig {
    public static final String COUNT = "count";
    public static final String IS_DONE = "is_done";
    public static final String DONE_PROGRESS = "done_progress";
    public static final String IS_CANCELED= "is_canceled";
    public static final String DISCOVERY_FIELD= "discovery_field";

    @NacosValue(value = "${job.interactive.timeout}", autoRefreshed = true)
    private int interactiveTimeout;
    @NacosValue(value = "${job.response.timeout}", autoRefreshed = true)
    private int responseTimeout;
    @NacosValue(value = "${job.execution.timeout}", autoRefreshed = true)
    private int executionTimeout;
    @NacosValue(value = "${job.pool.corePoolSize}", autoRefreshed = true)
    private int poolCorePoolSize;
    @NacosValue(value = "${job.pool.maxPoolSize}", autoRefreshed = true)
    private int poolMaxPoolSize;
    @NacosValue(value = "${job.pool.queueCapacity}", autoRefreshed = true)
    private int poolQueueCapacity;
}
