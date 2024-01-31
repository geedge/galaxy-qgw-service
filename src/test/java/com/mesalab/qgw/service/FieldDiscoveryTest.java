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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @Author ww
 * @Date 2024/1/30
 */
@EnableAutoConfiguration
public class FieldDiscoveryTest extends GalaxyQGWApplicationTests {

    @Autowired
    private QueryController queryController;
    private static final List<String> fields = new ArrayList<>();

    static {
        fields.add("flags");
        fields.add("security_rule_list");
        fields.add("client_port");
        fields.add("server_ip");
    }

    @Test
    public void testFieldDiscoveryDefault() {
        DSLQueryRequestParam body = jsonToInParameter("parameters/fieldDiscoveryTest.json", "field_discovery_default", DSLQueryRequestParam.class);
        body.getCustomRequestParam().put("custom.field_discovery.fields", fields);
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
    public void testFieldDiscoverySessions() {
        DSLQueryRequestParam body = jsonToInParameter("parameters/fieldDiscoveryTest.json", "field_discovery_sessions", DSLQueryRequestParam.class);
        body.getCustomRequestParam().put("custom.field_discovery.fields", fields);
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
    public void testFieldDiscoveryBytes() {
        DSLQueryRequestParam body = jsonToInParameter("parameters/fieldDiscoveryTest.json", "field_discovery_bytes", DSLQueryRequestParam.class);
        body.getCustomRequestParam().put("custom.field_discovery.fields", fields);
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
    public void testFieldDiscoveryIncomingBytes() {
        DSLQueryRequestParam body = jsonToInParameter("parameters/fieldDiscoveryTest.json", "field_discovery_incoming_bytes", DSLQueryRequestParam.class);
        body.getCustomRequestParam().put("custom.field_discovery.fields", fields);
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
    public void testFieldDiscoveryOutgoingBytes() {
        DSLQueryRequestParam body = jsonToInParameter("parameters/fieldDiscoveryTest.json", "field_discovery_outgoing_bytes", DSLQueryRequestParam.class);
        body.getCustomRequestParam().put("custom.field_discovery.fields", fields);
        BaseResult commitResult = queryController.createDSLQuery(body);
        Assert.assertTrue(commitResult.getMessage(), commitResult.getStatus() == 201);
    }
}
