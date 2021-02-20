package com.mesalab.qgw.controller;

import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.configuration.ProjectProperties;
import com.mesalab.qgw.service.DiagnosisService;
import com.mesalab.qgw.service.impl.TestSqlServiceImpl;
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

    private final ProjectProperties projectProperties;

    private final Environment environment;

    @Autowired
    private TestSqlServiceImpl testSqlService;

    @Autowired
    private DiagnosisService diagnosisService;

    @Autowired
    public TestController(ProjectProperties projectProperties, Environment environment) {
        this.projectProperties = projectProperties;
        this.environment = environment;
    }

    /**
     * 测试
     * 使用@GetMapping相当于使用@RequestMapping(method={RequestMethod.GET})
     *
     * @return BaseResult
     */
    @GetMapping(value = "projectProperties")
    public BaseResult projectProperties() {
        log.info("Project Properties: {}", projectProperties);
        //此处本意是将projectProperties返回至前端，但是projectProperties对象是由spring注入而来，其中包含过多的动态代理数据，
        //使用lombok的@Data注解处理BaseResult时，数据过大，返回报错，因此只返回success
        return BaseResultGenerator.success();
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
        String[] env = projectProperties.getEnv();
        log.info("Project env: {}", Arrays.toString(env));
        return BaseResultGenerator.success(env);
    }

    /**
     * 是否是生产环境
     *
     * @return 返回当前项目的运行环境
     */
    @GetMapping(value = "isProduct")
    public BaseResult isProduct() {
        boolean isProduct = projectProperties.isProduct();
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
        BaseResult result = testSqlService.runSql();
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

}
