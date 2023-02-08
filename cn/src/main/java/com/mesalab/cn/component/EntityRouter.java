package com.mesalab.cn.component;


import com.mesalab.cn.enums.QueryType;
import com.mesalab.cn.service.EntityService;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.DSLObject;
import com.mesalab.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author wWei
 * @description:
 * @date 2020/11/12 5:39 下午
 */
@Component
public class EntityRouter {

    @Autowired
    private EntityService entityService;

    public BaseResult executeRouter(DSLObject dslObject) throws BusinessException {
        switch (QueryType.getByQueryType(dslObject.getQuery().getQueryType())) {
            case SHARE_NET:
                return entityService.shareNetQuery(dslObject);
            case ENTITY_DETAIL:
                return entityService.entityDetailQuery(dslObject);
            default:
                return entityService.bannedAppQuery(dslObject);
        }
    }
}
