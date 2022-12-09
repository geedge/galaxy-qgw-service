package com.mesalab.service.service;

import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.ComDSLObject;


public interface RelationService {

    BaseResult getRealRelation(String param, ComDSLObject dslObject);
}
