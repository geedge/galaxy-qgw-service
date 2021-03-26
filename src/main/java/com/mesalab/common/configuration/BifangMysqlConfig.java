package com.mesalab.common.configuration;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @Date: 2020-09-17 15:03
 * @Author : liuyongqiang
 * @ClassName : BifangMysqlConfiguration
 * @Description : BifangMysql数据源配置
 */
@Data
@Component
public class BifangMysqlConfig {

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String userName;

    @Value("${spring.datasource.pin}")
    private String pin;

}
