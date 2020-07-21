package com.mesalab.gateway.service.impl;

import com.mesalab.common.base.BaseResult;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.gateway.dsldefine.DSLObject;
import com.mesalab.gateway.service.DruidEngineService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * @Date: 2020-07-20 10:05
 * @Author : liuyongqiang
 * @ClassName : DruidEngineServiceImpl
 * @Description : Druid数据查询引擎实现
 */
@Service("DruidEngineService")
public class DruidEngineServiceImpl implements DruidEngineService {


    @Override
    public BaseResult commonQuery(DSLObject dslObject) throws BusinessException {
        //TODO
        return null;
    }
}
