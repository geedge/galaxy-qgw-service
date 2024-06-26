package com.mesalab.qgw.controller;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.services.configuration.ThreadPoolMonitor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/v1/admin")
public class SystemController {
    private static final Log log = LogFactory.get();
    @Autowired
    ThreadPoolMonitor threadPoolMonitor;

    @GetMapping(value = "/clear/realJobQueue")
    public BaseResult clearTaskQueue() {
        log.info("Clear real job queue.");
        threadPoolMonitor.clearQueue();
        return BaseResultGenerator.success();
    }
}
