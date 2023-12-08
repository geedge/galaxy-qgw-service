package com.mesalab.qgw.service;

import com.mesalab.common.entity.BaseResult;
import com.mesalab.qgw.model.basic.DslQueryRequest;
import com.mesalab.qgw.model.basic.SqlQueryRequest;

import java.util.List;

/**
 * TODO
 *
 * @Classname QueryJobService
 * @Date 2023/12/6 18:09
 * @Author wWei
 */
public interface QueryJobService {

    BaseResult commitSqlSavedQuery(SqlQueryRequest request);

    BaseResult commitSqlAdHoc(SqlQueryRequest request);

    BaseResult adHocResult(String id);

    BaseResult savedQueryResult(String id);

    BaseResult adHocStatus(String id);

    BaseResult savedQueryStatus(String id);

    BaseResult commitDslAdHoc(DslQueryRequest request);

    BaseResult savedQueryResultBatch(List<String> ids);

    BaseResult adHocResultBatch(List<String> ids);

    BaseResult savedQueryStatusBatch(List<String> ids);

    BaseResult adHocStatusBatch(List<String> ids);
}
