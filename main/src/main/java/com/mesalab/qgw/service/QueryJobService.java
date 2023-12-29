package com.mesalab.qgw.service;

import com.mesalab.common.entity.BaseResult;
import com.mesalab.qgw.model.basic.DslQueryRequestParam;
import com.mesalab.qgw.model.basic.SqlQueryRequestParam;

import java.util.List;

/**
 *
 * @Date 2023/12/6 18:09
 * @Author wWei
 */
public interface QueryJobService {

    /**
     * Create an SQL saved query and return the query job id. It will storage the query job and results in the database.
     * The query job will be executed in the background through the SAVED-QUERY-SCHEDULER.
     * @param sqlQueryRequestParam
     * @return {@link BaseResult}
     * @created by wWei
     * @date 2023/12/11 11:02
     */
    BaseResult createSQLSavedQuery(SqlQueryRequestParam sqlQueryRequestParam);

    /**
     * Create an SQL ad-hoc query and return the query job id. The query will be executed immediately.
     * It Support three modes of execution: NORMAL, BLOCKING, and ONESHOT.
     *   NORMAL: The query will be executed in the asynchronous thread pool.
     *   BLOCKING: The query will return the jobID when the query is completed (Sync Mode).
     *   ONESHOT: The query will return the results in the same call (Sync Mode). Does not return the job ID.
     * @param sqlQueryRequestParam
     * @return {@link BaseResult}
     * @created by wWei
     * @date 2023/12/11 11:28
     */
    BaseResult createSQLAdHocQuery(SqlQueryRequestParam sqlQueryRequestParam);

    /**
     * Create a DSL ad-hoc query and return the query job id. The query will be executed immediately.
     * It Support three modes of execution: NORMAL, BLOCKING, and ONESHOT[Recommend].
     * @param dslQueryRequestParam
     * @return {@link BaseResult}
     * @return
     */
    BaseResult createDSLAdHocQuery(DslQueryRequestParam dslQueryRequestParam);

    BaseResult getAdHocQueryResultById(String id);

    BaseResult getSavedQueryResultById(String id);

    BaseResult getAdHocQueryStatusById(String id);

    BaseResult getSavedQueryStatusById(String id);


    BaseResult getSavedQueryResult(List<String> ids);

    BaseResult getAdHocQueryResult(List<String> ids);

    BaseResult getSavedQueryStatus(List<String> ids);

    BaseResult getAdHocQueryStatus(List<String> ids);

    BaseResult deleteSavedQueryById(String id);
}
