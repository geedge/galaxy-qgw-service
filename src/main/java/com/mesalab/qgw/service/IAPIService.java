package com.mesalab.qgw.service;

import com.mesalab.common.base.BaseResult;
import com.mesalab.qgw.model.api.QueryParam;

public interface IAPIService {
    BaseResult executeQuery(QueryParam param);
}
