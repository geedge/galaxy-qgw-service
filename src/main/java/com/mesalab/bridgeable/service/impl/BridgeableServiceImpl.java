package com.mesalab.bridgeable.service.impl;

import com.jfinal.plugin.activerecord.Db;
import com.mesalab.bridgeable.service.BridgeableService;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @Date: 2020-09-18 11:00
 * @Author : liuyongqiang
 * @ClassName : BridgeableServiceImpl
 * @Description : BridgeableServiceImpl
 */
@Service("bridgeableService")
public class BridgeableServiceImpl implements BridgeableService {

    /**
     * @Description: APP-ID字典列表
     * @Author: liuyongqiang
     * @Date: 2020/9/17 15:31
     * @return: com.mesalab.common.base.BaseResult
     **/
    @Override
    public BaseResult appDicList() {
        List<?> list = Db.query(Db.getSql("appDicList"));
        return BaseResultGenerator.success(list);
    }
}
