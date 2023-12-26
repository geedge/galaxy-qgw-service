package com.mesalab.qgw.service;

import com.mesalab.common.entity.BaseResult;
import com.mesalab.qgw.model.job.EncryptionInfo;

public interface UtilService {
    BaseResult getSQLSyntaxTree(String sql);

    BaseResult getCiphertext(EncryptionInfo param);
}
