package com.mesalab.qgw.service;

import com.mesalab.common.entity.BaseResult;
import java.util.Map;


public interface HosService {

    BaseResult getFileList(String bucketName, String prefix, Map<String, String> udfMetaParam);
}
