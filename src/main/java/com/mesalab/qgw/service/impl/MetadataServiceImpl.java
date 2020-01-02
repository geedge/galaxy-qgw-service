package com.mesalab.qgw.service.impl;

import com.mesalab.common.base.BaseResult;
import com.mesalab.common.enums.BaseResultEnum;
import com.mesalab.common.enums.QueryOptionEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.service.IAPIService;
import com.mesalab.qgw.service.MetadataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public  class MetadataServiceImpl implements MetadataService {

    @Autowired
    IAPIService apiService;

    @Override
    public BaseResult getSchemaInfo(String type, String name) {

        ApiParam param = new ApiParam();
        param.setOption(QueryOptionEnum.QUERY_SCHEMA.getValue());
        if("tables".equals(type)){//查询库中所有表
            param.setSchema(name);
            param.setQuery("show tables");
        }else if("fields".equals(type)){//查询表字段具体信息
            param.setTableName(name);
            param.setQuery("DESCRIBE " + name);
        }else {
            throw new BusinessException(BaseResultEnum.NOT_FOUND.getCode(), "Not found, check url please", null);
        }
        return apiService.executeQuery(param);
    }
}
