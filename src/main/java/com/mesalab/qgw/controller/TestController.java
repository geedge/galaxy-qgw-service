package com.mesalab.qgw.controller;

import com.google.common.collect.Lists;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.nacos.NacosConfig;
import com.mesalab.knowledge.common.config.ArangoConfig;
import com.mesalab.qgw.model.basic.HttpConfig;
import com.mesalab.qgw.model.basic.*;
import com.mesalab.qgw.service.DiagnosisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 测试Controller
 * 使用@RestController可以不需要使用@ResponseBody注解，方法会自动处理成json格式返回
 *
 * @author darnell
 * @date 2019/5/13
 */
@Slf4j
@RestController
@RequestMapping(value = "test")
public class TestController {

    @Autowired
    TroubleshootingController troubleshootingController;
    @Autowired
    private ProjectInfo projectProperties;
    @Autowired
    private JobAdminHttpSource jobAdminHttpSource;
    @Autowired
    private DruidIoHttpSource druidIoHttpSource;
    @Autowired
    private ClickHouseHttpSource clickHouseHttpSource;
    @Autowired
    private HBaseAPISource hBaseAPISource;
    @Autowired
    private EngineConfigSource engineConfigSource;
    @Autowired
    private ArangoConfig arangoConfig;
    @Autowired
    private HttpConfig httpConfig;
    @Autowired
    private NacosConfig nacosConfig;

    /**
     * 测试
     * 使用@GetMapping相当于使用@RequestMapping(method={RequestMethod.GET})
     *
     * @return BaseResult
     */
    @GetMapping(value = "projectProperties")
    @AuditLog("TestController.projectProperties")
    public BaseResult projectProperties() {
        log.info("Project Properties: {}", projectProperties);
        return BaseResultGenerator.success("ok", projectProperties);
    }

    /**
     * 获取项目环境
     *
     * @return 返回当前项目的环境值
     */
    @GetMapping(value = "activeProfiles")
    @AuditLog("TestController.activeProfiles")
    public BaseResult activeProfiles() {
        List<String> activeProfiles = Lists.newArrayList(nacosConfig.getNamespace());
        log.info("Current Namespace: {}", activeProfiles);
        return BaseResultGenerator.success(activeProfiles);
    }

    /**
     * 运行环境
     *
     * @return 返回当前项目的运行环境
     */
    @GetMapping(value = "env")
    @AuditLog("TestController.env")
    public BaseResult env() {
        return BaseResultGenerator.success(Lists.newArrayList(nacosConfig.getNamespace()));
    }

    /**
     * 检查是否授权
     *
     * @return 检查授权
     */
    @GetMapping(value = "checkAuthc")
    @AuditLog("TestController.checkAuthc")
    public BaseResult checkAuthc() {
        return BaseResultGenerator.success();
    }

    /**
     * 检查job动态连接配置
     *
     * @return
     */
    @GetMapping(value = "jobAdminHttpSource")
    @AuditLog("TestController.jobAdminHttpSource")
    public BaseResult jobAdminHttpSource() {
        return BaseResultGenerator.success("ok", jobAdminHttpSource);
    }

    /**
     * 检查druid动态连接配置
     *
     * @return
     */
    @GetMapping(value = "druidIoHttpSource")
    @AuditLog("TestController.druidIoHttpSource")
    public BaseResult druidIoHttpSource() {
        return BaseResultGenerator.success("ok", druidIoHttpSource);
    }

    /**
     * 检查clickHouse动态连接配置
     *
     * @return
     */
    @GetMapping(value = "clickHouseHttpSource")
    @AuditLog("TestController.clickHouseHttpSource")
    public BaseResult clickHouseHttpSource() {
        return BaseResultGenerator.success("ok", clickHouseHttpSource);
    }

    /**
     * 检查hBase动态连接配置
     *
     * @return
     */
    @GetMapping(value = "hBaseAPISource")
    @AuditLog("TestController.hBaseAPISource")
    public BaseResult hBaseAPISource() {
        return BaseResultGenerator.success("ok", hBaseAPISource);
    }

    /**
     * 检查engine动态连接配置
     *
     * @return
     */
    @GetMapping(value = "engineConfigSource")
    @AuditLog("TestController.engineConfigSource")
    public BaseResult engineConfigSource() {
        return BaseResultGenerator.success("ok", engineConfigSource);
    }

    /**
     * 检查arango动态连接配置
     *
     * @return
     */
    @GetMapping(value = "arangoConfig")
    @AuditLog("TestController.arangoConfig")
    public BaseResult arangoConfig() {
        return BaseResultGenerator.success("ok", arangoConfig);
    }

    /**
     * 检查httpConfig动态连接配置
     *
     * @return
     */
    @GetMapping(value = "httpConfig")
    @AuditLog("TestController.httpConfig")
    public BaseResult httpConfig() {
        return BaseResultGenerator.success("ok", httpConfig);
    }

}
