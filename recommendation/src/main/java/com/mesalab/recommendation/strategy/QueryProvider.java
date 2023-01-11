package com.mesalab.recommendation.strategy;

import com.mesalab.recommendation.entity.DSLObject;

/**
 * @Description: 提供查询结果
 */
public interface QueryProvider {

    public Object query(DSLObject.DSLQuery dslQeruy);

}
