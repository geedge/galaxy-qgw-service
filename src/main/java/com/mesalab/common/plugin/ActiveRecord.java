package com.mesalab.common.plugin;

import com.jfinal.plugin.activerecord.ActiveRecordPlugin;
import com.jfinal.plugin.druid.DruidPlugin;
import com.mesalab.common.configuration.BifangMysqlConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * @Date: 2020-09-17 15:24
 * @Author : liuyongqiang
 * @ClassName : ActiveRecord
 * @Description : ActiveRecord
 */
@Slf4j
@Component
public class ActiveRecord {

    @Autowired
    BifangMysqlConfiguration bmc;

    @PostConstruct
    public void initDialect(){
        DruidPlugin dp = new DruidPlugin(bmc.getUrl(),bmc.getUserName(),bmc.getPassWord());
        ActiveRecordPlugin arp = new ActiveRecordPlugin(dp);
        arp.addSqlTemplate("sql-temelate.sql");
        dp.start();
        arp.start();
        log.info("Initializing ActiveRecordPlugin Complete");
    }
}
