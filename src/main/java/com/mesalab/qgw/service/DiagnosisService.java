package com.mesalab.qgw.service;

import com.mesalab.common.base.BaseResult;

public interface DiagnosisService {

    BaseResult validateSchema();

    BaseResult validateMetadata();

    BaseResult runPocSQL(boolean isSaveResult);

    BaseResult getPocSQL(int queryNo);





}
