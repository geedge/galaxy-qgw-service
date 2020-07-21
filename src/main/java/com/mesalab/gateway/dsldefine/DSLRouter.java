package com.mesalab.gateway.dsldefine;

import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.gateway.service.ArangoDBEngineService;
import com.mesalab.gateway.service.ClickHouseEngineService;
import com.mesalab.gateway.service.DruidEngineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @Date: 2020-07-20 18:11
 * @Author : liuyongqiang
 * @ClassName : DSLRouter
 * @Description : DSL路由执行,负责调用查询引擎执行查询
 */
@Component
public class DSLRouter {

    @Autowired
    private ArangoDBEngineService arangoDBEngineService;

    @Autowired
    private ClickHouseEngineService clickHouseEngineService;

    @Autowired
    private DruidEngineService druidEngineService;

    //TODO
    //1.最好设计成Spring容器中的Bean
    //2.提示信息以BusinessException抛出
    public BaseResult executeRoute(DSLObject dslObject) throws BusinessException {
        switch (dslObject.getDbType()){
            case "ArangoDB"://用枚举实现
                return arangoDBEngineService.commonQuery(dslObject);
            case "Druid":
                return druidEngineService.commonQuery(dslObject);
            case "ClickHouse":
                return clickHouseEngineService.commonQuery(dslObject);
            default:
                return BaseResultGenerator.failure(ResultStatusEnum.FAIL.getCode(),ResultStatusEnum.FAIL.getMessage());
        }
    }

}
