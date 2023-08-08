package com.mesalab.qgw.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.nacos.NacosConfig;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.qgw.service.MonitorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


/**
 * @Auther: lijinyang
 * @Date: 2023/04/23/11:23
 */
@Service("monitorService")
public class MonitorServiceImpl implements MonitorService {

    @Autowired
    private NacosConfig nacosConfig;
    @Autowired
    private ConfigService systemConfigService;

    private final static String DATA_ID = "version.json";

    @Override
    public JSONObject getVersionInfo() {
        JSONObject version = null;
        try {
            String content = systemConfigService.getConfig(DATA_ID, nacosConfig.getGroup(), 3000);
            version = JSONObject.parseObject(content);
        } catch (NacosException e) {
            e.printStackTrace();
        }
        return version;
    }

}
