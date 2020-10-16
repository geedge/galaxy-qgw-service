package com.mesalab.openapi.controller;

import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.openapi.service.OpenApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

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
    public BaseResult appDicList() {
        return openApiService.appDicList();
    }

    /**
     * @param id: 报告ID
     * @Description: 查询报告结构
     * @Author: liuyongqiang
     * @Date: 2020/10/16 17:46
     * @return: com.mesalab.common.base.BaseResult
     **/
    @GetMapping("reportResults/{id}")
    public BaseResult reportResults(@PathVariable(name = "id") Integer id) {
        return openApiService.reportResults(id);
    }
}
