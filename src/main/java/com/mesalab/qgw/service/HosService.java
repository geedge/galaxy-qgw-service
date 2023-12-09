package com.mesalab.qgw.service;

import com.mesalab.common.entity.BaseResult;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;


public interface HosService {

    BaseResult getFileList(String bucketName, String prefix);
}
