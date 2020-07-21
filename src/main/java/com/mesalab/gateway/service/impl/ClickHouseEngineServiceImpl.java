package com.mesalab.gateway.service.impl;

import com.mesalab.common.base.BaseResult;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.gateway.dsldefine.DSLObject;
import com.mesalab.gateway.service.ClickHouseEngineService;
import org.springframework.stereotype.Service;

/**
 * @Date: 2020-07-20 10:04
 * @Author : liuyongqiang
 * @ClassName : ClickHouseEngineServiceImpl
 * @Description : ClickHouse数据查询引擎实现
 */
@Service("ClickHouseEngineService")
public class ClickHouseEngineServiceImpl implements ClickHouseEngineService {


    @Override
    public BaseResult commonQuery(DSLObject dslObject) throws BusinessException {
        //TODO
        return null;
    }
}
