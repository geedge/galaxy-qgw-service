package com.mesalab.qgw.service;

import com.mesalab.common.entity.BaseResult;
import com.mesalab.qgw.model.basic.QueryContext;

public interface SQLSyncQueryService {

    BaseResult executeQuery(QueryContext param);

}