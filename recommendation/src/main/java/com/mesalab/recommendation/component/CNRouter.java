package com.mesalab.recommendation.component;


import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.DSLObject;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.recommendation.enums.CDQueryType;
import com.mesalab.recommendation.service.CNService;
import com.mesalab.recommendation.service.EntityDetailService;
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

    @Autowired
    private EntityDetailService entityDetailService;

    public BaseResult executeRouter(DSLObject dslObject) throws BusinessException {
        switch (CDQueryType.getByQueryType(dslObject.getQuery().getQueryType())) {
            case SHARE_NET:
                return cnService.shareNetQuery(dslObject);
            case ENTITY_DETAIL:
                return entityDetailService.entityDetailQuery(dslObject);
            default:
                return cnService.bannedAppQuery(dslObject);
        }
    }
}
