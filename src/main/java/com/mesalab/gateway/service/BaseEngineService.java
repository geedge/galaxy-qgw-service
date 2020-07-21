package com.mesalab.gateway.service;

import com.mesalab.common.base.BaseResult;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.gateway.dsldefine.DSLObject;

/**
 * @Date: 2020-07-20 18:39
 * @Author : liuyongqiang
 * @ClassName : BaseEngineService
 * @Description : BaseEngineService
 */
public interface BaseEngineService {

    /**
     * @Description: 单表公共查询接口
     * @Author: liuyongqiang
     * @Date: 2020/7/21 9:41
     * @param dslObject: DSL查询对象
     * @return: com.mesalab.common.base.BaseResult
     **/
    BaseResult commonQuery(DSLObject dslObject)throws BusinessException;

}
