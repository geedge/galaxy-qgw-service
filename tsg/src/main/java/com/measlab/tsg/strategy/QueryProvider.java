package com.measlab.tsg.strategy;

import com.measlab.tsg.entity.DSLObject;

/**
 * @Description: 提供查询结果
 */
public interface QueryProvider {

    public Object query(DSLObject.DSLQuery dslQeruy);

}
