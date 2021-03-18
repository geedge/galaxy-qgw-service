package com.mesalab.qgw.controller;

import com.mesalab.qgw.model.api.VersionInfo;
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
    private VersionInfo version;

    @RequestMapping("/info")
    public VersionInfo config() {
        return version;
    }
}
