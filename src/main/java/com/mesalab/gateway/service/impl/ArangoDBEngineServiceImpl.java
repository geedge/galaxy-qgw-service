package com.mesalab.gateway.service.impl;

import com.mesalab.common.base.BaseResult;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.gateway.dsldefine.DSLObject;
import com.mesalab.gateway.service.ArangoDBEngineService;
import org.springframework.stereotype.Service;

/**
 * @Date: 2020-07-20 10:03
 * @Author : liuyongqiang
 * @ClassName : ArangoDBEngineServiceImpl
 * @Description : ArangoDB数据查询引擎实现
 */
@Service("ArangoDBEngineService")
public class ArangoDBEngineServiceImpl implements ArangoDBEngineService {



    @Override
    public BaseResult commonQuery(DSLObject dslObject) throws BusinessException {

        return null;
    }

    @Override
    public BaseResult queryFqdn(DSLObject dslObject) throws BusinessException {
        return null;
    }
}
