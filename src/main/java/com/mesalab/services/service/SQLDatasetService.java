package com.mesalab.services.service;

import com.google.common.collect.Lists;
import com.mesalab.common.entity.BaseResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SQL数据集市管理服务
 *
 * @Classname SQLDatasetService
 * @Date 2023/8/10 13:54
 * @Author wWei
 */
public interface SQLDatasetService
{

    /**
     * Desc: 获取动态变量
     *
     * @param
     * @return {@link List<LinkedHashMap>}
     * @created by wWei
     * @date 2023/8/16 19:39
     */
    List<LinkedHashMap> getVariable();

    /**
     * Desc: 获取单个SQL模版
     *
     * @param datasetId
     * @return {@link Map<String, Object>}
     * @created by wWei
     * @date 2023/8/17 09:45
     */
    Map<String, Object> getDataset(String datasetId);

    /**
     * Desc: 批量获取SQL模版
     *
     * @param
     * @return {@link Map<String, Object>}
     * @created by wWei
     * @date 2023/8/16 15:31
     */
    Map<String, Object> getDatasets(List<String> ids, String category, String title);

    /**
     * Desc: 结果预览
     *
     * @param datasetId
     * @return {@link BaseResult}
     * @created by wWei
     * @date 2023/8/16 19:33
     */
    BaseResult getPreview(String datasetId);
}
