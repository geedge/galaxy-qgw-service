package com.mesalab.services.service;

import com.mesalab.common.base.BaseResult;
import com.mesalab.services.common.dsl.ComDSLObject;


public interface EntityService {
    BaseResult getEntityInfo(String param, ComDSLObject dslObject);
}
