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
public interface SQLDatasetService {

    /**
     * Desc: 通过queryId查询SQL任务执行状态
     *
     * @param queryId
     * @return {@link BaseResult}
     * @created by wWei
     * @date 2021/1/7 6:49 下午
     */
    BaseResult getProcessesByQueryId(String queryId);

    /**
     * 终止查询任务
     *
     * @param queryId
     * @return
     */
    BaseResult deleteQueryTask(String queryId);

    /**
     * 自定义查询ID: 由 Catalog（数据库类型): resultID+query 组成。
     *
     * @param resultId
     * @param query
     * @return {@link String}
     * @created by wWei
     * @date 2021/1/7 6:48 下午
     */
    String getCustomQueryId(String resultId, String query);
}
