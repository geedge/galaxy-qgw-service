package com.mesalab.knowledge.strategy;

import com.mesalab.knowledge.entity.DSLObject;

/**
 * @Description: 提供查询结果
 */
public interface QueryProvider {

    public Object query(DSLObject.DSLQuery dslQeruy);

}
