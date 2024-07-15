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
public class RecommendTest extends GalaxyQGWApplicationTests {

    @Autowired
    private QueryController queryController;

    @Test
    public void testIpLearningFqdnRelateIp() {
        DSLQueryRequestParam body = jsonToInParameter("parameters/recommendTest.json", "ip_learning_fqdn_relate_ip", DSLQueryRequestParam.class);
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
    public void testIpLearningActiveIpOneshot() {
        DSLQueryRequestParam body = jsonToInParameter("parameters/recommendTest.json", "ip_learning_active_ip", DSLQueryRequestParam.class);
        BaseResult commitResult = queryController.createDSLQuery(body);
        Assert.assertTrue(commitResult.getMessage(), commitResult.getStatus() == 200);
    }
}
