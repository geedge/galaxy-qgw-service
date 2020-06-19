package com.mesalab.common.configuration;

import com.mesalab.qgw.model.api.ClickHouseHttpSource;
import com.zdjizhi.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
@EnableConfigurationProperties(ClickHouseProperties.class)
public class ClickHouseConfiguration {

    private final ClickHouseProperties clickhouseProperties;

    @Autowired
    public ClickHouseConfiguration(ClickHouseProperties clickhouseProperties) {
        this.clickhouseProperties = clickhouseProperties;
    }

    @Bean(name="clickHouseHttpSource")
    public ClickHouseHttpSource clickhouseHttpSource() {
        ClickHouseHttpSource httpSource  = new ClickHouseHttpSource();
        httpSource.setUrl(StringUtil.substringAfter(clickhouseProperties.getUrl(), "//"));

        DBAccount realTermAccount = new DBAccount();
        realTermAccount.setDbname(clickhouseProperties.getRealTimeAccount().getDbname());
        realTermAccount.setUsername(clickhouseProperties.getRealTimeAccount().getUsername());
        realTermAccount.setPassword(clickhouseProperties.getRealTimeAccount().getPassword());
        httpSource.setRealTimeAccount(realTermAccount);

        DBAccount longTermAccount = new DBAccount();
        longTermAccount.setDbname(clickhouseProperties.getLongTimeAccount().getDbname());
        longTermAccount.setUsername(clickhouseProperties.getLongTimeAccount().getUsername());
        longTermAccount.setPassword(clickhouseProperties.getLongTimeAccount().getPassword());
        httpSource.setLongTimeAccount(longTermAccount);

        return httpSource;
    }

}
