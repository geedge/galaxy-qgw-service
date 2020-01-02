package com.mesalab.qgw.service;

import com.mesalab.common.base.BaseResult;

public interface SystemService {

    /**
     * 系统存储配额
     * @return
     */
    BaseResult getStorageQuota();
}
