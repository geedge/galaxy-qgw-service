package com.mesalab.qgw.model.basic;

import com.alibaba.nacos.api.config.annotation.NacosValue;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Data
@Component
public class JobAdminHttpSource implements Serializable {
    /**
     * xxl-job-admin的url地址
     */
    @NacosValue(value = "${xxl-job-admin.url}", autoRefreshed = true)
    private String url;
    /**
     * xxl-job-admin的用户名
     */
    @NacosValue(value = "${xxl-job-admin.userName}")
    private String userName;
    /**
     * xxl-job-admin的密码
     */
    @NacosValue(value = "${xxl-job-admin.pin}")
    private String pin;
}
