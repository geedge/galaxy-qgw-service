package com.mesalab.qgw.model.api;

import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Data
@Component
@NacosConfigurationProperties(prefix = "xxl-job-admin", dataId = "galaxy-qgw-service", type = ConfigType.YAML, autoRefreshed = true)
public class JobAdminHttpSource implements Serializable {

    private String url;

    private String userName;

    private String password;

}
