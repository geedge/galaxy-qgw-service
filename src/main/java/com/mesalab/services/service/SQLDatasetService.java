package com.mesalab.services.service;

import com.mesalab.common.entity.BaseResult;

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

}
