package com.mesalab.openapi.controller;

import com.mesalab.openapi.service.OpenApiService;
import com.mesalab.common.base.BaseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Date: 2020-09-18 10:58
 * @Author : liuyongqiang
 * @ClassName : OpenApiController
 * @Description : OpenApiController
 */
@RestController
@RequestMapping("open-api")
public class OpenApiController {

    @Autowired
    OpenApiService openApiService;

    /**
     * @Description: APP字典列表
     * @Author: liuyongqiang
     * @Date: 2020/9/17 15:32
     * @return: com.mesalab.common.base.BaseResult
     **/
    @GetMapping("appDicList")
    public BaseResult appDicList(){
        return openApiService.appDicList();
    }
}
