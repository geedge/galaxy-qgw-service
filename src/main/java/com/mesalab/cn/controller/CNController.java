package com.mesalab.cn.controller;

import com.mesalab.cn.common.pojo.DSLObject;
import com.mesalab.cn.component.CNRouter;
import com.mesalab.cn.component.DslValidate;
import com.mesalab.common.base.BaseResult;
import com.zdjizhi.utils.JsonMapper;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@RestController
@RequestMapping("/cn")
public class CNController {

    @Autowired
    protected CNRouter cnRouter;
    @Autowired
    protected DslValidate dslValidate;


    @PostMapping("/v1/illegalAccess")
    public BaseResult query(@RequestBody DSLObject dslObject) {
        log.info("CN违规接入行为信息查询接口, 参数: dslObject is {}", JsonMapper.toJsonString(dslObject));
        dslValidate.executeValidate(dslObject);
        return cnRouter.executeRouter(dslObject);
    }
}
