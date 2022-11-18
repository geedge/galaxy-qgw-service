package com.mesalab.services.service;

import com.mesalab.common.entity.BaseResult;
import com.mesalab.services.common.dsl.ComDSLObject;


public interface RelationService {

    BaseResult getRealRelation(String param, ComDSLObject dslObject);
}
