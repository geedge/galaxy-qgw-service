package com.mesalab.qgw.service;

import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.common.entity.BaseResult;

public interface ApiService {

    BaseResult executeQuery(ApiParam param);

}