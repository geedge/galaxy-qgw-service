package com.mesalab.qgw.service;

import com.mesalab.common.entity.BaseResult;
import com.mesalab.qgw.model.basic.SQLQueryContext;

public interface SQLSyncQueryService {

    BaseResult executeQuery(SQLQueryContext param);

}