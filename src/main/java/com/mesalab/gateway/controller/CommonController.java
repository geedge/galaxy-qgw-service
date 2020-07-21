package com.mesalab.gateway.controller;

import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.gateway.dsldefine.DSLObject;
import com.mesalab.gateway.dsldefine.DSLRouter;
import com.mesalab.gateway.service.ArangoDBEngineService;
import com.mesalab.gateway.service.ClickHouseEngineService;
import com.mesalab.gateway.service.DruidEngineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Date: 2020-07-20 10:46
 * @Author : liuyongqiang
 * @ClassName : CommonController
 * @Description : 公共查询接口定义
 */
@Slf4j
@RestController
@RequestMapping("/common")
public class CommonController {

    @Autowired
    protected DSLRouter dslRouter;

    @Autowired
    protected DruidEngineService druidEngine;

    @Autowired
    protected ArangoDBEngineService arangoDBEngine;

    @Autowired
    protected ClickHouseEngineService clickHouseEngine;

    @PostMapping("/query")
    public BaseResult query(@RequestBody DSLObject dslObject){
        return BaseResultGenerator.success();
    }

}
