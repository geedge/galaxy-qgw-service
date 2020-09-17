package com.mesalab.qgw.service;

import com.mesalab.common.base.BaseResult;
import com.mesalab.qgw.model.api.ApiParam;

public interface ApiService {

    BaseResult executeQuery(ApiParam param);

    /**
     * @Description: APP-ID字典列表
     * @Author: liuyongqiang
     * @Date: 2020/9/17 15:31
     * @return: com.mesalab.common.base.BaseResult
     **/
    BaseResult appIdDicList();
}