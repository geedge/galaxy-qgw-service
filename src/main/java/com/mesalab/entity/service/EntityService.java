package com.mesalab.entity.service;

import com.mesalab.common.base.BaseResult;
import com.mesalab.entity.entity.DSLObject;


public interface EntityService {
    BaseResult getDslInfo(String param, DSLObject dslObject);
}
