package com.mesalab.qgw.model.api;

import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Component
@NacosConfigurationProperties(prefix = "xxl-job-admin", dataId = "galaxy-qgw-service", type = ConfigType.YAML, autoRefreshed = true)
public class JobAdminHttpSource implements Serializable {

    private String url;

    private String userName;

    private String pin;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPin() {
        return pin;
    }

    public void setPin(String pin) {
        this.pin = pin;
    }
}
