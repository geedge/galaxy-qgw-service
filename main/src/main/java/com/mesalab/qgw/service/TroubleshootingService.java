package com.mesalab.qgw.service;

import com.alibaba.fastjson.JSONObject;
import com.mesalab.common.entity.BaseResult;

public interface TroubleshootingService {
    JSONObject getComponentStatus();

    BaseResult consistencyCheck();

    BaseResult validateMetadata();

    BaseResult benchmarkTest(String test, boolean isSaved, String category);


}
