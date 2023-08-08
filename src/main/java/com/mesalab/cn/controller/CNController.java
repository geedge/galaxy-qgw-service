package com.mesalab.cn.controller;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson2.JSON;
import com.mesalab.cn.entity.pojo.DSLObject;
import com.mesalab.cn.component.CNRouter;
import com.mesalab.cn.component.DslValidate;
import com.mesalab.common.entity.BaseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * @ClassName : DslQueryController
 * @Description : 违规接入行为信息查询接口
 * @Date: 2020-08-17 10:55
 * @Author : wWei
 */
@RestController
@RequestMapping("/cn")
public class CNController {

    private static final Log log = LogFactory.get();

    @Autowired
    private CNRouter cnRouter;
    @Autowired
    private DslValidate dslValidate;


    @PostMapping("/v1/illegalAccess")
    public BaseResult query(@RequestBody DSLObject dslObject) {
        log.info("CN违规接入行为信息查询接口, 参数: dslObject is {}", JSON.toJSONString(dslObject));
        dslValidate.executeValidate(dslObject);
        return cnRouter.executeRouter(dslObject);
    }
    @PostMapping("/v1/entityExplorer")
    public BaseResult queryEntity(@RequestBody DSLObject dslObject) {
        log.info("网络监视者实体详情查询接口, 参数: dslObject is {}", JSON.toJSONString(dslObject));
        dslValidate.executeValidate(dslObject);
        return cnRouter.executeRouter(dslObject);
    }
}
