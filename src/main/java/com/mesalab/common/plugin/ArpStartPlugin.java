package com.mesalab.common.plugin;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.jfinal.plugin.activerecord.ActiveRecordPlugin;
import com.jfinal.plugin.activerecord.IDataSourceProvider;

/**
 * @Date: 2020-10-09 16:09
 * @Author : liuyongqiang
 * @ClassName : ArpStartPlugin
 * @Description : ArpStartPlugin
 */
public class ArpStartPlugin extends ActiveRecordPlugin {

    private static final Log log = LogFactory.get();

    public ArpStartPlugin(IDataSourceProvider dataSourceProvider) {
        super(dataSourceProvider);
    }

    @Override
    public boolean start() {
        try {
            super.start();
        } catch (RuntimeException e) {
            log.error("ArpStartPlugin init error:{}", e.getMessage());
        }
        return true;
    }
}
