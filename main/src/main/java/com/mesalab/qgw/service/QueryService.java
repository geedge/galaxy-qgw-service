package com.mesalab.qgw.service;

import com.mesalab.common.entity.BaseResult;
import com.mesalab.qgw.model.basic.QueryProfile;

@Deprecated
public interface QueryService {

    BaseResult executeQuery(QueryProfile param);

}