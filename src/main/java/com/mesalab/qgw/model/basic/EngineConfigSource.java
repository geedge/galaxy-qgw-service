package com.mesalab.qgw.model.basic;

import com.alibaba.nacos.api.config.annotation.NacosValue;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Data
@Component
public class EngineConfigSource implements Serializable {

    @NacosValue(value = "${engine.maxCacheNum}", autoRefreshed = true)
    private int maxCacheNum;
    @NacosValue(value = "${engine.defaultResultNum}", autoRefreshed = true)
    private int defaultResultNum;
    @NacosValue(value = "${engine.sampleDataPeriod}", autoRefreshed = true)
    private int sampleDataPeriod;
    @NacosValue(value = "${engine.fieldDiscovery.topK}", autoRefreshed = true)
    private int fieldDiscoveryTopK;

}
