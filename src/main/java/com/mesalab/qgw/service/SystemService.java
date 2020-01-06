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
     * @param start_time
     * @param end_time
     * @return
     */
    BaseResult dailyTrendOfStorage(String start_time, String end_time);
}
