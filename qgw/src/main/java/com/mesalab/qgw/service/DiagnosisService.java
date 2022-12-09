package com.mesalab.qgw.service;


import com.mesalab.common.entity.BaseResult;

public interface DiagnosisService {

    BaseResult validateSchema();

    BaseResult validateMetadata();

    BaseResult runPocSQL(boolean isSaveResult, String option);

    BaseResult getPocSQL(String dialect, Integer queryNo);

    BaseResult getMessageInfo(String param);
}
