package com.mesalab.cn.component;


import com.mesalab.cn.common.enums.CDQueryType;
import com.mesalab.cn.common.pojo.DSLObject;
import com.mesalab.cn.service.CNService;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author wWei
 * @description:
 * @date 2020/11/12 5:39 下午
 */
@Component
public class CNRouter {

    @Autowired
    private CNService cnService;

    public BaseResult executeRouter(DSLObject dslObject) throws BusinessException {
        switch (CDQueryType.getByQueryType(dslObject.getQuery().getQueryType())) {
            case SHARE_NET:
                return cnService.shareNetQuery(dslObject);
            default:
                return cnService.bannedAppQuery(dslObject);
        }
    }
}
