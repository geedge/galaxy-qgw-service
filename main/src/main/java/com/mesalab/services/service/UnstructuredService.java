package com.mesalab.services.service;

import com.mesalab.common.entity.BaseResult;
import com.mesalab.services.common.dsl.ComDSLObject;

import java.util.Map;

public interface UnstructuredService {
    Map<String, Object> getUnstructuredFields();

    BaseResult getUnstructuredData(String param, ComDSLObject dslObject);
}
