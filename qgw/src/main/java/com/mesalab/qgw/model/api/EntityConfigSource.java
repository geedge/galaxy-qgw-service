package com.mesalab.qgw.model.api;

import com.alibaba.nacos.api.config.annotation.NacosValue;
import lombok.Data;
import org.springframework.stereotype.Component;

/**
 * 实体推荐默认num
 *
 * @Classname EntityConfigSource
 * @Date 2021/11/29 10:02 下午
 * @Author wWei
 */
@Data
@Component
public class EntityConfigSource {

    /**
     * 默认活跃客户端IP数量
     */
    @NacosValue(value = "${service.entity.activeClientIP}", autoRefreshed = true)
    private int activeClientIP;

    /**
     * 默认活跃服务端IP数量(TCPBySession)
     */
    @NacosValue(value = "${service.entity.topServerIP.TCPBySession}", autoRefreshed = true)
    private int topServerIPByTCPSession;

    /**
     * 默认活跃服务端IP数量(TCPByUniqClientIP)
     */
    @NacosValue(value = "${service.entity.topServerIP.TCPByUniqClientIP}", autoRefreshed = true)
    private int topServerIPByTCPUniqClientIP;

    /**
     * 默认活跃服务端IP数量(UDPBySession)
     */
    @NacosValue(value = "${service.entity.topServerIP.UDPBySession}", autoRefreshed = true)
    private int topServerIPByUDPBySession;

    /**
     * 默认活跃服务端IP数量(UDPByUniqClientIP)
     */
    @NacosValue(value = "${service.entity.topServerIP.UDPByUniqClientIP}", autoRefreshed = true)
    private int topServerIPByUDPUniqClientIP;

    /**
     * 默认访问量最高SNI数量
     */
    @NacosValue(value = "${service.entity.topSNI.defaultSize}", autoRefreshed = true)
    private int topSNIDefaultSize;

    /**
     * 待推荐SNI数据集
     */
    @NacosValue(value = "${service.entity.topSNI.dataset}", autoRefreshed = true)
    private int topSNIDataset;

    /**
     * 待推荐GTP-C
     */
    @NacosValue(value = "${service.entity.gtpc.defaultSize}", autoRefreshed = true)
    private int topGTPCDefaultSize;

}
