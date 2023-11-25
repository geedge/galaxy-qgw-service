package com.mesalab.qgw.service;

import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.services.controller.JobController;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author wxs
 * @Date 2022/9/28
 */
@EnableAutoConfiguration
public class JobTest extends GalaxyQGWApplicationTests {

    @Autowired
    private JobController jobController;

    @Test
    public void fieldDiscoveryDefault() {
        HashMap param = jsonToInParameter("parameters/jobTest.json", "field_discovery_default", HashMap.class);
        BaseResult committed = jobController.commit(param);
        Assert.assertTrue(committed.getMessage(), committed.getStatus() == 201);
        String id = ((List<Map>) committed.getData()).get(0).get("job_id").toString();
        DeferredResult<BaseResult> baseResultDeferredResult = jobController.longPollingLongTermResult(id);
        if(baseResultDeferredResult.hasResult()){
            BaseResult result = (BaseResult) baseResultDeferredResult.getResult();
            Assert.assertTrue(result.getMessage(), result.getStatus() == 200);
        }
    }

    @Test
    public void fieldDiscoveryBytes() {
        HashMap param = jsonToInParameter("parameters/jobTest.json", "field_discovery_bytes", HashMap.class);
        BaseResult committed = jobController.commit(param);
        Assert.assertTrue(committed.getMessage(), committed.getStatus() == 201);
        String id = ((List<Map>) committed.getData()).get(0).get("job_id").toString();
        DeferredResult<BaseResult> baseResultDeferredResult = jobController.longPollingLongTermResult(id);
        if(baseResultDeferredResult.hasResult()){
            BaseResult result = (BaseResult) baseResultDeferredResult.getResult();
            Assert.assertTrue(result.getMessage(), result.getStatus() == 200);
        }
    }

    @Test
    public void longTerm() {
        HashMap param = jsonToInParameter("parameters/jobTest.json", "long_term", HashMap.class);
        BaseResult committed = jobController.commit(param);
        Assert.assertTrue(committed.getMessage(), committed.getStatus() == 201);
        String id = ((List<Map>) committed.getData()).get(0).get("job_id").toString();
        DeferredResult<BaseResult> baseResultDeferredResult = jobController.longPollingLongTermResult(id);
        if(baseResultDeferredResult.hasResult()){
            BaseResult result = (BaseResult) baseResultDeferredResult.getResult();
            Assert.assertTrue(result.getMessage(), result.getStatus() == 200);
        }

    }

    @Test
    public void report() {
        HashMap param = jsonToInParameter("parameters/jobTest.json", "report", HashMap.class);
        BaseResult committed = jobController.commit(param);
        Assert.assertTrue(committed.getMessage(), committed.getStatus() == 201);
        String id = ((List<Map>) committed.getData()).get(0).get("job_id").toString();
        BaseResult result = jobController.getStatus(id);
        Assert.assertTrue(result.getMessage(), result.getStatus() == 200);
        BaseResult cancelled = jobController.cancel(id);
        Assert.assertTrue(cancelled.getMessage(), cancelled.getStatus() == 202);

    }

    @Test
    public void statistics() {
        HashMap param = jsonToInParameter("parameters/jobTest.json", "statistics_top", HashMap.class);
        BaseResult committed = jobController.commit(param);
        Assert.assertTrue(committed.getMessage(), committed.getStatus() == 201);
        String id = ((List<Map>) committed.getData()).get(0).get("job_id").toString();
        DeferredResult<BaseResult> baseResultDeferredResult = jobController.longPollingLongTermResult(id);
        if(baseResultDeferredResult.hasResult()){
            BaseResult result = (BaseResult) baseResultDeferredResult.getResult();
            Assert.assertTrue(result.getMessage(), result.getStatus() == 200);
        }
    }

//    @Test
//    public void getResultReport() {
//        BaseResult result = jobController.getResultReport(jobId);
//        Assert.assertTrue(result.getMessage(), result.getStatus() == 200);
//    }

}
