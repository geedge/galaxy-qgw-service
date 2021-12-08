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
public class TaskCfg {

    @NacosValue(value = "${task.runningTTL}", autoRefreshed = true)
    private int runningTTL;
    @NacosValue(value = "${task.progressTTL}", autoRefreshed = true)
    private int progressTTL;
    @NacosValue(value = "${task.task.corePoolSize}", autoRefreshed = true)
    private int taskCorePoolSize;
    @NacosValue(value = "${task.task.maxPoolSize}", autoRefreshed = true)
    private int taskMaxPoolSize;
    @NacosValue(value = "${task.task.keepAliveTime}", autoRefreshed = true)
    private int taskKeepAliveTime;
    @NacosValue(value = "${task.task.queueCapacity}", autoRefreshed = true)
    private int taskQueueCapacity;
    @NacosValue(value = "${task.subTask.corePoolSize}", autoRefreshed = true)
    private int subTaskCorePoolSize;
    @NacosValue(value = "${task.subTask.maxPoolSize}", autoRefreshed = true)
    private int subTaskMaxPoolSize;
    @NacosValue(value = "${task.subTask.keepAliveTime}", autoRefreshed = true)
    private int subTaskKeepAliveTime;
    @NacosValue(value = "${task.subTask.queueCapacity}", autoRefreshed = true)
    private int subTaskQueueCapacity;

}
