package com.mesalab.bridgeable.controller;

import com.mesalab.bridgeable.service.BridgeableService;
import com.mesalab.common.base.BaseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Date: 2020-09-18 10:58
 * @Author : liuyongqiang
 * @ClassName : BridgeableController
 * @Description : BridgeableController
 */
@RestController
@RequestMapping("bridgeable")
public class BridgeableController {

    @Autowired
    BridgeableService bs;

    /**
     * @Description: APP-ID字典列表
     * @Author: liuyongqiang
     * @Date: 2020/9/17 15:32
     * @return: com.mesalab.common.base.BaseResult
     **/
    @GetMapping("appDicList")
    public BaseResult appDicList(){
        return bs.appDicList();
    }
}
