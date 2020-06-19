package com.mesalab.qgw.service;

import com.mesalab.common.base.BaseResult;
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
}
