package com.mesalab.qgw.service;

import com.mesalab.common.entity.BaseResult;
import com.mesalab.qgw.model.basic.DSLQueryRequestParam;
import com.mesalab.qgw.model.basic.QueryCache;

/**
 * TODO
 *
 * @Classname LogQueryService
 * @Date 2024/9/18 11:38
 * @Author wWei
 */
public interface LogQueryService {

    BaseResult run(DSLQueryRequestParam param);

    BaseResult getQueryListByQueryContext(QueryCache queryCache, String fields, int limit, int offset);
    BaseResult getQueryCountByQueryContext(QueryCache queryCache);
    BaseResult getQueryTimelineByQueryContext(QueryCache queryCache);
}
