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
    @NacosValue(value = "${engine.trafficSpectrum.cIpConnectAppTopK}", autoRefreshed = true)
    private int trafficSpectrumCIpConnectAppTopK;
}
