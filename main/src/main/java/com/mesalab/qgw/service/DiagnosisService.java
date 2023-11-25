package com.mesalab.qgw.service;

import com.alibaba.fastjson.JSONObject;
import com.mesalab.common.entity.BaseResult;

public interface DiagnosisService {

    BaseResult validateSchema();

    BaseResult validateMetadata();

    BaseResult runPocSQL(boolean isSaveResult, String option, String category);

    BaseResult getPocSQL(String dialect, Integer queryNo);

    BaseResult getMessageInfo(String param);

    JSONObject getVersionInfo();


    /**
     * Desc: 一致性校验
     *
     * @param
     * @return {@link BaseResult}
     * @created by wWei
     * @date 2022/6/5 9:24 下午
     */
    BaseResult consistencyCheck();
}
