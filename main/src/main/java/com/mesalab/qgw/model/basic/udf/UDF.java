package com.mesalab.qgw.model.basic.udf;

import com.mesalab.common.entity.BaseResult;
import com.mesalab.qgw.model.basic.SQLQuerySource;

public interface UDF {

    Object execute(SQLQuerySource sqlQuerySource,  BaseResult baseResult);
}
