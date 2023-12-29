package com.mesalab.qgw.service.impl;

import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.qgw.service.HosService;
import org.springframework.stereotype.Service;

@Service(value = "hosService")
public class HosServiceImp implements HosService {
    @Override
    public BaseResult getFileList(String bucketName, String prefix) {

        return BaseResultGenerator.success();
    }
}
