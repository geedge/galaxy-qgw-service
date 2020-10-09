package com.mesalab.common.plugin;

import com.jfinal.plugin.activerecord.ActiveRecordPlugin;
import com.jfinal.plugin.activerecord.IDataSourceProvider;
import lombok.extern.slf4j.Slf4j;

/**
 * @Date: 2020-10-09 16:09
 * @Author : liuyongqiang
 * @ClassName : ArpStartPlugin
 * @Description : ArpStartPlugin
 */
@Slf4j
public class ArpStartPlugin extends ActiveRecordPlugin {

    public ArpStartPlugin(IDataSourceProvider dataSourceProvider) {
        super(dataSourceProvider);
    }

    @Override
    public boolean start() {
        try {
            super.start();
        } catch (Exception e) {
            log.error("ArpStartPlugin init error:{}", e.getMessage());
        }
        return true;
    }
}
