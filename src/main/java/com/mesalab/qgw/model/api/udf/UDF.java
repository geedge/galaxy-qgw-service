package com.mesalab.qgw.model.api.udf;

import com.mesalab.qgw.model.api.SQLQuerySource;

import java.util.List;

public interface UDF {

    Object execute(SQLQuerySource sqlQuerySource,  List<Object> results);
}
