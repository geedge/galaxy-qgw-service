package com.mesalab.knowledge.router;

import com.mesalab.knowledge.common.config.ArangoConfig;
import com.mesalab.knowledge.entity.DSLObject;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @Description: 提供查询语言
 */
public interface QLProvider {

    public Object query(DSLObject.DSLQuery dslQeruy, ArangoConfig arangoConfig);

}
