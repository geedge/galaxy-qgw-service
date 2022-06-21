package com.mesalab.services.service;

import com.mesalab.common.base.BaseResult;
import com.mesalab.services.common.dsl.ComDSLObject;

import java.util.List;
import java.util.Map;


public interface EntityService {

    /**
     * Desc: TODO
     *
     * @param param
     * @param dslObject
     * @return {@link com.mesalab.common.base.BaseResult}
     * @created by wWei
     * @date 2022/6/21 4:41 下午
     */
    BaseResult getEntityInfo(String param, ComDSLObject dslObject);

    /**
     * Desc: TODO
     *
     * @param data
     * @return {@link List< Map< String, Object>>}
     * @created by wWei
     * @date 2022/6/21 4:41 下午
     */
    List<Map<String, Object>> mergeSNI(List<Map<String, Object>> data);
}

