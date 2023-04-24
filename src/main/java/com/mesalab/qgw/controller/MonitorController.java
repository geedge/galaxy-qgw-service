package com.mesalab.qgw.controller;

import com.alibaba.fastjson.JSONObject;
import com.mesalab.qgw.service.MonitorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author wangwei
 * @description:
 * @date 2020/7/31 1:59 下午
 */
@RestController
@RequestMapping(value = "/monitor")
public class MonitorController {

    @Autowired
    private MonitorService monitorService;

    @RequestMapping("/info")
    public JSONObject config() {
        return monitorService.getVersionInfo();
    }
}
