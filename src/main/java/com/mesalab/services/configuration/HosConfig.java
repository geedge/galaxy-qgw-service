package com.mesalab.services.configuration;

import com.alibaba.nacos.api.config.annotation.NacosValue;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * @Author wxs
 * @Date 2022/7/5
 */
@Data
@Component
public class HosConfig implements Serializable {


    @NacosValue(value = "${hos.uri}", autoRefreshed = true)
    private String uri;

    @NacosValue(value = "${hos.token}", autoRefreshed = true)
    private String token;

    @NacosValue(value = "${hos.bucket}", autoRefreshed = true)
    private String bucket;

    @NacosValue(value = "${hos.multiUploadStartSize}", autoRefreshed = true)
    private Long multiUploadStartSize;

    @NacosValue(value = "${hos.multiUploadPartSize}", autoRefreshed = true)
    private Long multiUploadPartSize;

}
