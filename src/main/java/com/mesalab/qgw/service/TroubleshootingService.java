package com.mesalab.qgw.service;

import com.alibaba.fastjson2.JSONObject;
import com.mesalab.common.entity.BaseResult;

public interface TroubleshootingService {
    JSONObject getComponentStatus();

    BaseResult consistencyCheck();

    BaseResult validateMetadata();

    BaseResult benchmarkTest(String test, boolean isSaved);

    BaseResult datesetVerification();
}
