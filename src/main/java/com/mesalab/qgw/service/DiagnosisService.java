package com.mesalab.qgw.service;

import com.alibaba.fastjson.JSONObject;
import com.mesalab.common.entity.BaseResult;

public interface DiagnosisService {

    BaseResult validateSchema();

    BaseResult getPocSQL(String dialect, Integer queryNo);


    JSONObject getVersionInfo();
}
