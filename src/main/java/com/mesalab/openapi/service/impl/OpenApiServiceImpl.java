package com.mesalab.openapi.service.impl;

import com.jfinal.plugin.activerecord.Db;
import com.mesalab.openapi.service.OpenApiService;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @Date: 2020-09-18 11:00
 * @Author : liuyongqiang
 * @ClassName : OpenApiServiceImpl
 * @Description : OpenApiServiceImpl
 */
@Service("openApiService")
public class OpenApiServiceImpl implements OpenApiService {

    /**
     * @Description: APP字典列表
     * @Author: liuyongqiang
     * @Date: 2020/9/17 15:31
     * @return: com.mesalab.common.base.BaseResult
     **/
    @Override
    public BaseResult appDicList() {
        List<?> list = Db.query(Db.getSql("appDicList"));
        return BaseResultGenerator.success(list);
    }
}
