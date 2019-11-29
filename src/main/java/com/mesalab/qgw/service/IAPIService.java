package com.mesalab.qgw.service;

import com.mesalab.common.base.BaseResult;
import com.mesalab.qgw.model.api.ApiParam;

import java.util.Map;

public interface IAPIService {
    BaseResult executeQuery(ApiParam param);
}