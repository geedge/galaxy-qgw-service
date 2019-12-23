package com.mesalab.qgw.controller;

import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.configuration.ProjectProperties;
import com.mesalab.common.utils.SpringContextUtil;
import com.mesalab.qgw.service.impl.HttpClientService;
import com.mesalab.qgw.service.impl.TestSqlServiceImpl;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.*;

/**
 * 测试Controller
 * 使用@RestController可以不需要使用@ResponseBody注解，方法会自动处理成json格式返回
 *
 * @author darnell
 * @date 2019/5/13
 */
@Slf4j
@Api(value = "/test", tags = "测试接口模块")
@RestController
@RequestMapping(value = "test")
public class TestController {

    private final ProjectProperties projectProperties;

    private final Environment environment;

    @Autowired
    TestSqlServiceImpl testSqlService;

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
    @ApiOperation(value = "项目配置", notes = "获取项目配置参数")
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
    @ApiOperation(value = "项目环境值", notes = "获取当前项目运行时环境值，取Environment对象中的activeProfiles, 项目运行时的值")
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
    @ApiOperation(value = "项目环境值", notes = "获取当前项目运行环境，取ProjectProperties.env的值，应该和activeProfiles一致")
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
    @ApiOperation(value = "是否生产环境", notes = "检查是否是生产环境")
    @GetMapping(value = "isProduct")
    public BaseResult isProduct() {
        boolean isProduct = projectProperties.isProduct();
        String msg = "Current Environment is" + (isProduct ? "" : " not") + " product";
        log.info(msg);
        return BaseResultGenerator.success(msg);
    }

    /**
     * 检查是否授权
     *
     * @return 检查授权
     */
    @ApiOperation(value = "检查授权", notes = "检查授权，如果授权成功：返回success，否则跳转/login")
    @GetMapping(value = "checkAuthc")
    public BaseResult checkAuthc() {
        return BaseResultGenerator.success();
    }


    /**
     * 运行测试用例
     * @return
     */
    @ApiOperation(value = "测试用例", notes = "运行测试用例")
    @GetMapping(value = "runSql")
    public BaseResult runSql(){
        BaseResult result = testSqlService.runSql();
        return BaseResultGenerator.success(result.getMessage(), result.getData());
    }

    /**
     * 校验schema
     *
     * @return
     */
    @ApiOperation(value = "校验schema", notes = "运行校验schema")
    @GetMapping(value = "runSchema")
    public BaseResult runSchema() {
        BaseResult result = testSqlService.runSchema();
        return result;
    }

}
