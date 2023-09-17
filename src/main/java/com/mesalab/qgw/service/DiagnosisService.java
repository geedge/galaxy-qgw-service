package com.mesalab.qgw.service;

import com.mesalab.common.entity.BaseResult;

public interface DiagnosisService {

    BaseResult validateSchema();

    BaseResult validateMetadata();

    BaseResult runPocSQL(boolean isSaveResult, String option, String category);

    BaseResult getPocSQL(String category, String dialect, Integer queryNo);

    BaseResult getMessageInfo(String param);
}
