package com.mesalab.network.service;

import com.mesalab.common.base.BaseResult;
import com.mesalab.network.dsl.DSLObject;

/**
 * @author wangwei
 * @version 1.0
 * @date 2020/6/30 4:20 下午
 */


public interface NetworkMonitorService {

    BaseResult build(DSLObject dslObject);

}
