package com.measlab.tsg.service;

import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.ComDSLObject;

import java.util.List;
import java.util.Map;


public interface EntityService {

    /**
     * Desc: TODO
     *
     * @param param
     * @param dslObject
     * @return {@link BaseResult}
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

    /**
     *
     * @param map
     * @Description 构建域名Hash表
     * @date 2022/7/8 13:56
     */
    Map<String, List<String>> buildDomainHashTable(Map<String, List<String>> map);

    /**
     *
     * @param domain
     * @return boolean
     * @Description 判断domain是否属于CDN域名列表
     * @date 2022/7/7 17:52
     */
    boolean inCDN(Map<String, List<String>> cdnMap, String domain);
}

