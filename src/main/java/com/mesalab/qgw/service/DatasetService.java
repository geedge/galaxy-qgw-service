package com.mesalab.qgw.service;

import com.google.common.collect.Lists;
import com.mesalab.common.entity.BaseResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据集市管理服务
 *
 * @Classname DatasetService
 */
public interface DatasetService
{
    /**
     * Desc: 获取动态变量
     *
     * @param
     * @return {@link List<LinkedHashMap>}
     */
    List<LinkedHashMap> getVariable();

    /**
     * Desc: 获取单个SQL模版
     *
     * @param datasetId
     * @return {@link Map<String, Object>}
     */
    Map<String, Object> getDataset(String datasetId);

    /**
     * Desc: 批量获取SQL模版
     *
     * @param
     * @return {@link List<Map<String, String>>}
     */
    List<Map<String, Object>> getDatasets(List<String> ids, String category, String backendEngine);

    /**
     * Desc: 结果预览
     *
     * @param datasetId
     * @return {@link BaseResult}
     */
    BaseResult getPreview(String datasetId);

    /**
     * Desc: 获取执行SQL
     *
     * @param
     * @return
     */
    String buildExecSQL(List<LinkedHashMap> variables, String sql);

}
