package com.mesalab.qgw.service;

import com.mesalab.common.base.BaseResult;
import com.mesalab.qgw.model.job.EncryptionInfo;
import com.mesalab.qgw.model.job.StorageDeletionInfo;

import java.util.List;

public interface SystemService {

    /**
     * 系统存储配额
     * @return
     */
    BaseResult getStorageQuota();

    /**
     * 获取每天日志存储变化
     * @param searchStartTime
     * @param searchEndTime
     * @return
     */
    BaseResult dailyTrendOfStorage(String searchStartTime, String searchEndTime);

    /**
     * 数据配额设置
     * @param list
     * @return
     */
    BaseResult deleteStorage(List<StorageDeletionInfo> list);

    /**
     * 数据配额设置状态
     * @param logType
     * @return
     */
    BaseResult getDeleteStorageStatus(String logType);

    /**
     * 终止查询任务
     * @param queryId
     * @return
     */
    BaseResult deleteQueryTask(String queryId);

    /**
     * 构建查询唯一标识：Query ID，由 Catalog（数据库类型): resultID+query 组成。
     *
     * @param resultId
     * @param query
     * @return {@link String}
     * @created by wWei
     * @date 2021/1/7 6:48 下午
     */
    String getQueryId(String resultId, String query);

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
     * Desc: 通过queryId查询SQL任务执行状态
     *
     * @param param
     * @return {@link BaseResult}
     * @created by wWei
     * @date 2021/1/7 6:49 下午
     */
    BaseResult getCiphertext(EncryptionInfo param);
}
