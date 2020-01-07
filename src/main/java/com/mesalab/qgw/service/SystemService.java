package com.mesalab.qgw.service;

import com.mesalab.common.base.BaseResult;

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
}
