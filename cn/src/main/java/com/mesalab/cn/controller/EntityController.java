package com.mesalab.cn.controller;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.mesalab.cn.entity.DslValidate;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.DSLObject;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.cn.component.EntityRouter;
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
@RestController(value = "CNEntityController")
@RequestMapping("/cn")
public class EntityController {

    private static final Log log = LogFactory.get();

    @Autowired
    private EntityRouter cnRouter;
    @Autowired
    private DslValidate dslValidate;


    @PostMapping("/v1/illegalAccess")
    public BaseResult query(@RequestBody DSLObject dslObject) {
        log.info("CN违规接入行为信息查询接口, 参数: dslObject is {}", JsonMapper.toJsonString(dslObject));
        dslValidate.executeValidate(dslObject);
        return cnRouter.executeRouter(dslObject);
    }
    @PostMapping("/v1/entityExplorer")
    public BaseResult queryEntity(@RequestBody DSLObject dslObject) {
        log.info("网络监视者实体详情查询接口, 参数: dslObject is {}", JsonMapper.toJsonString(dslObject));
        dslValidate.executeValidate(dslObject);
        return cnRouter.executeRouter(dslObject);
    }
}
