package com.mesalab.qgw.service.impl;

import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.qgw.service.HosService;
import com.mesalab.services.configuration.HosConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service(value = "hosService")
public class HosServiceImp implements HosService {
    @Override
    public BaseResult getFileList(String bucketName, String prefix) {

        return BaseResultGenerator.success();
    }
}
