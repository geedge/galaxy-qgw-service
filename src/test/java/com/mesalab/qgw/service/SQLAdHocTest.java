package com.mesalab.qgw.service;

import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.qgw.controller.QueryController;
import com.mesalab.qgw.model.basic.SqlQueryRequestParam;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.Map;

/**
 * @Author ww
 * @Date 2024/1/30
 */
@EnableAutoConfiguration
public class SQLAdHocTest extends GalaxyQGWApplicationTests {

    @Autowired
    private QueryController queryController;

    @Test
    public void testQuerySqlDefault() {
        SqlQueryRequestParam body = jsonToInParameter("parameters/sqlAdHocTest.json", "query_sql_default", SqlQueryRequestParam.class);
        BaseResult commitResult = queryController.createSQLQuery(body);
        Assert.assertTrue(commitResult.getMessage(), commitResult.getStatus() == 201);
        String id = ((Map<String, Object>) commitResult.getJob()).get("job_id").toString();
        DeferredResult<BaseResult> getResult = new DeferredResult<>();
        while (true) {
            if (getResult.isSetOrExpired()) {
                break;
            }
            getResult = queryController.getJobStatusById(id, 0);
        }
        BaseResult result = (BaseResult) getResult.getResult();
        Assert.assertTrue(result.getMessage(), result.getStatus() == 200);
    }

    @Test
    public void testQuerySqlOneshot() {
        SqlQueryRequestParam body = jsonToInParameter("parameters/sqlAdHocTest.json", "query_sql_oneshot", SqlQueryRequestParam.class);
        BaseResult baseResult = queryController.createSQLQuery(body);
        Assert.assertTrue(baseResult.getMessage(), baseResult.getStatus() == 200);
    }

    @Test
    public void testQuerySqlNormal() {
        SqlQueryRequestParam body = jsonToInParameter("parameters/sqlAdHocTest.json", "query_sql_normal", SqlQueryRequestParam.class);
        BaseResult commitResult = queryController.createSQLQuery(body);
        Assert.assertTrue(commitResult.getMessage(), commitResult.getStatus() == 201);
        String id = ((Map<String, Object>) commitResult.getJob()).get("job_id").toString();
        DeferredResult<BaseResult> getResult = new DeferredResult<>();
        while (true) {
            if (getResult.isSetOrExpired()) {
                break;
            }
            getResult = queryController.getJobStatus(id, 0);
        }
        BaseResult result = (BaseResult) getResult.getResult();
        Assert.assertTrue(result.getMessage(), result.getStatus() == 200);
    }

    @Test
    public void testQuerySqlBlocking() {
        SqlQueryRequestParam body = jsonToInParameter("parameters/sqlAdHocTest.json", "query_sql_blocking", SqlQueryRequestParam.class);
        BaseResult commitResult = queryController.createSQLQuery(body);
        Assert.assertTrue(commitResult.getMessage(), commitResult.getStatus() == 201);
        String id = ((Map<String, Object>) commitResult.getJob()).get("job_id").toString();
        DeferredResult<BaseResult> getResult = new DeferredResult<>();
        while (true) {
            if (getResult.isSetOrExpired()) {
                break;
            }
            getResult = queryController.getJobResultById(id, 0);
        }
        BaseResult result = (BaseResult) getResult.getResult();
        Assert.assertTrue(result.getMessage(), result.getStatus() == 200);
    }

    @Test
    public void testQuerySqlJson() {
        SqlQueryRequestParam body = jsonToInParameter("parameters/sqlAdHocTest.json", "query_sql_json", SqlQueryRequestParam.class);
        BaseResult commitResult = queryController.createSQLQuery(body);
        Assert.assertTrue(commitResult.getMessage(), commitResult.getStatus() == 201);
        String id = ((Map<String, Object>) commitResult.getJob()).get("job_id").toString();
        DeferredResult<BaseResult> getResult = new DeferredResult<>();
        while (true) {
            if (getResult.isSetOrExpired()) {
                break;
            }
            getResult = queryController.getJobResult(id, 0);
        }
        BaseResult result = (BaseResult) getResult.getResult();
        Assert.assertTrue(result.getMessage(), result.getStatus() == 200);
    }

    @Test
    public void testQuerySqlCsv() {
        SqlQueryRequestParam body = jsonToInParameter("parameters/sqlAdHocTest.json", "query_sql_csv", SqlQueryRequestParam.class);
        BaseResult commitResult = queryController.createSQLQuery(body);
        Assert.assertTrue(commitResult.getMessage(), commitResult.getStatus() == 201);
        String id = ((Map<String, Object>) commitResult.getJob()).get("job_id").toString();
        DeferredResult<BaseResult> getResult = new DeferredResult<>();
        while (true) {
            if (getResult.isSetOrExpired()) {
                break;
            }
            getResult = queryController.getJobResultById(id, 0);
        }
        BaseResult result = (BaseResult) getResult.getResult();
        Assert.assertTrue(result.getMessage(), result.getStatus() == 200);
    }
}
