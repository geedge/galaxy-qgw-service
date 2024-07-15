package com.mesalab.services.configuration;

import com.alibaba.nacos.api.config.annotation.NacosValue;
import lombok.Data;
import org.springframework.stereotype.Component;

/**
 * TODO
 *
 * @Classname TaskConfig
 * @Date 2024/6/26 10:55
 * @Author wWei
 */
@Data
@Component
public class TaskConfig {

    @NacosValue(value = "${task.pool.corePoolSize}", autoRefreshed = true)
    private int poolCorePoolSize;
    @NacosValue(value = "${task.pool.maxPoolSize}", autoRefreshed = true)
    private int poolMaxPoolSize;
    @NacosValue(value = "${task.pool.queueCapacity}", autoRefreshed = true)
    private int poolQueueCapacity;

}
