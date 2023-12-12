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

    /**
     * Desc: Commit a SQL Saved Query Job
     *
     * @param request
     * @return {@link BaseResult}
     * @created by wWei
     * @date 2023/12/11 11:02
     */
    BaseResult commitSqlSavedQuery(SqlQueryRequest request);

    /**
     * Desc: Commit a SQL AdHoc Query Job
     *
     * @param request
     * @return {@link BaseResult}
     * @created by wWei
     * @date 2023/12/11 11:28
     */
    BaseResult commitSqlAdHoc(SqlQueryRequest request);

    BaseResult getAdHocResult(String id);

    BaseResult getSavedQueryResult(String id);

    BaseResult getAdHocStatus(String id);

    BaseResult getSavedQueryStatus(String id);

    BaseResult commitDslAdHoc(DslQueryRequest request);

    BaseResult getSavedQueryResultBatch(List<String> ids);

    BaseResult getAdHocResultBatch(List<String> ids);

    BaseResult getSavedQueryStatusBatch(List<String> ids);

    BaseResult getAdHocStatusBatch(List<String> ids);

    BaseResult cancelSavedQuery(String id);
}
