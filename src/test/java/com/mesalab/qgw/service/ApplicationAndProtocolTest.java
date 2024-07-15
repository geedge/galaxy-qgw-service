package com.mesalab.qgw.service;

import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.qgw.controller.QueryController;
import com.mesalab.qgw.model.basic.DSLQueryRequestParam;
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
public class ApplicationAndProtocolTest extends GalaxyQGWApplicationTests {

    @Autowired
    private QueryController queryController;

    @Test
    public void testAppAndProtocolSummary() {
        DSLQueryRequestParam body = jsonToInParameter("parameters/applicationAndProtocolTest.json", "application_and_protocol_summary", DSLQueryRequestParam.class);
        BaseResult commitResult = queryController.createDSLQuery(body);
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
    public void testAppAndProtocolTreeComposition() {
        DSLQueryRequestParam body = jsonToInParameter("parameters/applicationAndProtocolTest.json", "application_and_protocol_tree_composition", DSLQueryRequestParam.class);
        BaseResult commitResult = queryController.createDSLQuery(body);
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
    public void testAppAndProtocolTreeThroughput() {
        DSLQueryRequestParam body = jsonToInParameter("parameters/applicationAndProtocolTest.json", "application_and_protocol_tree_throughput", DSLQueryRequestParam.class);
        BaseResult commitResult = queryController.createDSLQuery(body);
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
    public void testAppAndProtocolTopApp() {
        DSLQueryRequestParam body = jsonToInParameter("parameters/applicationAndProtocolTest.json", "application_and_protocol_top_apps", DSLQueryRequestParam.class);
        BaseResult commitResult = queryController.createDSLQuery(body);
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
    public void testAppAndProtocolTreeAppSummaryOneshot() {
        DSLQueryRequestParam body = jsonToInParameter("parameters/applicationAndProtocolTest.json", "application_and_protocol_app_summary", DSLQueryRequestParam.class);
        BaseResult commitResult = queryController.createDSLQuery(body);
        Assert.assertTrue(commitResult.getMessage(), commitResult.getStatus() == 200);
    }

    @Test
    public void testAppAndProtocolAppRelatedInternalIpsOneshot() {
        DSLQueryRequestParam body = jsonToInParameter("parameters/applicationAndProtocolTest.json", "application_and_protocol_app_related_internal_ips", DSLQueryRequestParam.class);
        BaseResult commitResult = queryController.createDSLQuery(body);
        Assert.assertTrue(commitResult.getMessage(), commitResult.getStatus() == 200);
    }

    @Test
    public void testAppAndProtocolAppThroughputOneshot() {
        DSLQueryRequestParam body = jsonToInParameter("parameters/applicationAndProtocolTest.json", "application_and_protocol_app_throughput", DSLQueryRequestParam.class);
        BaseResult commitResult = queryController.createDSLQuery(body);
        Assert.assertTrue(commitResult.getMessage(), commitResult.getStatus() == 200);
    }
}
