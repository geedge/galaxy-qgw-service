package com.mesalab.qgw.controller;

import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.configuration.ProjectProperties;
import com.mesalab.common.enums.EnvironmentEnum;
import com.mesalab.qgw.model.api.ClickHouseHttpSource;
import com.mesalab.qgw.model.api.DruidIoHttpSource;
import com.mesalab.qgw.model.api.JobAdminHttpSource;
import com.mesalab.qgw.service.DiagnosisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

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
    private Environment environment;
    @Autowired
    private DiagnosisService diagnosisService;
    @Autowired
    private ProjectProperties projectProperties;
    @Autowired
    private JobAdminHttpSource jobAdminHttpSource;
    @Autowired
    private DruidIoHttpSource druidIoHttpSource;
    @Autowired
    private ClickHouseHttpSource clickHouseHttpSource;

    /**
     * 测试
     * 使用@GetMapping相当于使用@RequestMapping(method={RequestMethod.GET})
     *
     * @return BaseResult
     */
    @GetMapping(value = "projectProperties")
    public BaseResult projectProperties() {
        log.info("Project Properties: {}", projectProperties);
        return BaseResultGenerator.success("ok", projectProperties);
    }

    /**
     * 获取项目环境值，获取的是Environment对象中的activeProfiles，String[]
     *
     * @return 返回当前项目的环境值
     */
    @GetMapping(value = "activeProfiles")
    public BaseResult activeProfiles() {
        String[] activeProfiles = environment.getActiveProfiles();
        log.info("Active Profiles: {}", Arrays.toString(activeProfiles));
        return BaseResultGenerator.success(activeProfiles);
    }

    /**
     * 运行环境，将Environment.activeProfiles注入到projectProfiles中
     *
     * @return 返回当前项目的运行环境
     */
    @GetMapping(value = "env")
    public BaseResult env() {
        return BaseResultGenerator.success(environment.getActiveProfiles());
    }

    /**
     * 是否是生产环境
     *
     * @return 返回当前项目的运行环境
     */
    @GetMapping(value = "isProduct")
    public BaseResult isProduct() {
        boolean isProduct = environment.acceptsProfiles(EnvironmentEnum.PROD.getName());
        String msg = "Current Environment is" + (isProduct ? "" : " not") + " product";
        return BaseResultGenerator.success(msg);
    }

    /**
     * 检查是否授权
     *
     * @return 检查授权
     */
    @GetMapping(value = "checkAuthc")
    public BaseResult checkAuthc() {
        return BaseResultGenerator.success();
    }


    /**
     * 运行测试用例
     *
     * @return
     */
    @GetMapping(value = "runSql")
    @Deprecated
    public BaseResult runSql() {
        BaseResult result = diagnosisService.runPocSQL(false);
        return result;
    }

    /**
     * 校验schema
     *
     * @return
     */
    @GetMapping(value = "runSchema")
    @Deprecated
    public BaseResult runSchema() {
        BaseResult result = diagnosisService.validateSchema();
        return result;
    }

    /**
     * 检查job动态连接配置
     *
     * @return
     */
    @GetMapping(value = "jobAdminHttpSource")
    public BaseResult jobAdminHttpSource() {
        return BaseResultGenerator.success("ok", jobAdminHttpSource);
    }

    /**
     * 检查druid动态连接配置
     *
     * @return
     */
    @GetMapping(value = "druidIoHttpSource")
    public BaseResult druidIoHttpSource(){
        return BaseResultGenerator.success("ok", druidIoHttpSource);
    }

    @GetMapping(value = "clickHouseHttpSource")
    public BaseResult clickHouseHttpSource(){
        return BaseResultGenerator.success("ok", clickHouseHttpSource);
    }

}
