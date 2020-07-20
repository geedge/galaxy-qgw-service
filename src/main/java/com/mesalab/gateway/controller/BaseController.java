package com.mesalab.gateway.controller;

import com.mesalab.gateway.service.ArangoDBEngineService;
import com.mesalab.gateway.service.ClickHouseEngineService;
import com.mesalab.gateway.service.DruidEngineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Date: 2020-07-20 10:46
 * @Author : liuyongqiang
 * @ClassName : BaseController
 * @Description : BaseController
 */
@RestController
public class BaseController {

    @Autowired
    private ArangoDBEngineService arangoDBEngineService;

    @Autowired
    private ClickHouseEngineService clickHouseEngineService;

    @Autowired
    private DruidEngineService druidEngineService;

}
