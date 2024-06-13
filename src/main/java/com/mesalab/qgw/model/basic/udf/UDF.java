package com.mesalab.qgw.model.basic.udf;

import com.mesalab.common.entity.BaseResult;
import com.mesalab.qgw.model.basic.SelectStatement;

public interface UDF {

    Object execute(SelectStatement selectStatement, boolean isDryRun, BaseResult baseResult);
}
