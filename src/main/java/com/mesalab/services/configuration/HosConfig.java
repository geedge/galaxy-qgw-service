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



    @NacosValue("${hos.uri}")
    private String uri;

    @NacosValue("${hos.token}")
    private String token;

    @NacosValue("${hos.bucket}")
    private String bucket;


}
