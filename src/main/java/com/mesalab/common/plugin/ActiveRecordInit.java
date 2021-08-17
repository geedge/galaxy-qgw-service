package com.mesalab.common.plugin;

import com.jfinal.plugin.druid.DruidPlugin;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * @Date: 2020-09-17 15:24
 * @Author : liuyongqiang
 * @ClassName : ActiveRecordInit
 * @Description : ActiveRecordInit
 */
@Slf4j
@Component
public class ActiveRecordInit {

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String userName;

    @Value("${spring.datasource.pin}")
    private String pin;

    @PostConstruct
    public void initDialect() {
        DruidPlugin dp = new DruidPlugin(url, userName, pin);
        ArpStartPlugin arp = new ArpStartPlugin(dp);
        arp.addSqlTemplate("sql-template.sql");
        dp.start();
        arp.start();
        log.info("Initializing ActiveRecordPlugin Complete");
    }
}
