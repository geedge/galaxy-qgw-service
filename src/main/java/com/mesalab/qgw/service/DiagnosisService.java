package com.mesalab.qgw.service;

import com.mesalab.common.base.BaseResult;

public interface DiagnosisService {

    BaseResult validateSchema();

    BaseResult validateMetadata();
}
