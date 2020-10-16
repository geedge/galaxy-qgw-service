package com.mesalab.openapi.service;

import com.mesalab.common.base.BaseResult;

/**
 * @Date: 2020-09-18 10:58
 * @Author : liuyongqiang
 * @ClassName : OpenApiService
 * @Description : OpenApiService
 */
public interface OpenApiService {

    /**
     * @Description: APP字典列表
     * @Author: liuyongqiang
     * @Date: 2020/9/17 15:31
     * @return: com.mesalab.common.base.BaseResult
     **/
    BaseResult appDicList();

    /**
     * @param id: 报告ID
     * @Description: 查询报告结果
     * @Author: liuyongqiang
     * @Date: 2020/10/16 17:45
     * @return: com.mesalab.common.base.BaseResult
     **/
    BaseResult reportResults(Integer id);
}
