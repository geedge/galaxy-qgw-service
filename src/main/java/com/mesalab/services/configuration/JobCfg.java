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
public class JobCfg {

    @NacosValue(value = "${job.runningTTL}", autoRefreshed = true)
    private int runningTTL;
    @NacosValue(value = "${job.progressTTL}", autoRefreshed = true)
    private int progressTTL;
    @NacosValue(value = "${job.pool.corePoolSize}", autoRefreshed = true)
    private int poolCorePoolSize;
    @NacosValue(value = "${job.pool.maxPoolSize}", autoRefreshed = true)
    private int poolMaxPoolSize;
    @NacosValue(value = "${job.pool.queueCapacity}", autoRefreshed = true)
    private int poolQueueCapacity;
}
