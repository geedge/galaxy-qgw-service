package com.mesalab.qgw.service;

import com.mesalab.common.entity.BaseResult;
import com.mesalab.qgw.model.basic.QueryContext;

@Deprecated
public interface QueryService {

    BaseResult executeQuery(QueryContext param);

}