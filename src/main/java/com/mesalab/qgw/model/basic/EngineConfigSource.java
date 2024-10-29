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
    @NacosValue(value = "${engine.fieldDiscovery.topK}", autoRefreshed = true)
    private int fieldDiscoveryTopK;
    @NacosValue(value = "${engine.packetCombine.delaySeconds}", autoRefreshed = true)
    private int packetCombineDelaySeconds;
    @NacosValue(value = "${engine.trafficSpectrum.clientIPAppResultNum}", autoRefreshed = true)
    private int trafficSpectrumClientIPAppResultNum;
    @NacosValue(value = "${engine.highLatency.threshold}", autoRefreshed = true)
    private int highLatencyThreshold;
    @NacosValue(value = "${engine.secretKey}", autoRefreshed = true)
    private String secretKey;
}
