package com.mesalab.qgw.service;

import com.mesalab.common.entity.BaseResult;
import com.mesalab.qgw.model.job.DataDecryptionRequest;
import com.mesalab.qgw.model.job.EncryptionInfo;

public interface UtilService {
    BaseResult getSQLSyntaxTree(String sql);

    BaseResult encryptWithJasypt(EncryptionInfo param);
    BaseResult decryptWithKMS(DataDecryptionRequest param);
}
