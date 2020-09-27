package com.mesalab.qgw.service;

import com.mesalab.common.base.BaseResult;
import com.mesalab.qgw.model.api.ApiParam;

public interface ApiService {

    BaseResult executeQuery(ApiParam param);

}