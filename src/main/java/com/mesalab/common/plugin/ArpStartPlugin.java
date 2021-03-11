package com.mesalab.common.plugin;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.jfinal.plugin.activerecord.ActiveRecordPlugin;
import com.jfinal.plugin.activerecord.IDataSourceProvider;
import lombok.extern.slf4j.Slf4j;

/**
 * @Date: 2020-10-09 16:09
 * @Author : liuyongqiang
 * @ClassName : ArpStartPlugin
 * @Description : ArpStartPlugin
 */
public class ArpStartPlugin extends ActiveRecordPlugin {
    public ArpStartPlugin(IDataSourceProvider dataSourceProvider) {
        super(dataSourceProvider);
    }

    @Override
    public boolean start() {
        super.start();
        return true;
    }
}
