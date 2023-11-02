package com.mesalab.qgw.service;

import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.services.controller.JobController;
import org.junit.Assert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.web.context.request.async.DeferredResult;
import org.testng.annotations.Test;

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

    private String jobId;

    @Test(priority = 1)
    public void commitFieldDiscoveryDefault() {
        HashMap map = jsonToInParameter("parameters/jobTest.json", "field_discovery_default", HashMap.class);
        BaseResult result = jobController.commit(map);
        Assert.assertTrue(result.getMessage(), result.getStatus() == 200);
        jobId = ((List<Map>) result.getData()).get(0).get("job_id").toString();

    }

    @Test(priority = 2)
    public void longPollingFieldDiscoveryDefaultResult() {
        DeferredResult<BaseResult> result = jobController.longPollingLongTermResult(jobId);
        BaseResult baseResult = (BaseResult) result.getResult();
        Assert.assertTrue(baseResult.getMessage(), baseResult.getStatus() == 200);

    }

    @Test(priority = 3)
    public void commitFieldDiscoveryBytes() {

        HashMap map = jsonToInParameter("parameters/jobTest.json", "field_discovery_bytes", HashMap.class);
        BaseResult result = jobController.commit(map);
        Assert.assertTrue(result.getMessage(), result.getStatus() == 200);
        jobId = ((List<Map>) result.getData()).get(0).get("job_id").toString();

    }

    @Test(priority = 4)
    public void longPollingFieldDiscoveryBytesResult() {

        DeferredResult<BaseResult> result = jobController.longPollingLongTermResult(jobId);
        BaseResult baseResult = (BaseResult) result.getResult();
        Assert.assertTrue(baseResult.getMessage(), baseResult.getStatus() == 200);

    }

    @Test(priority = 5)
    public void commitLongTerm() {

        HashMap map = jsonToInParameter("parameters/jobTest.json", "long-term", HashMap.class);
        BaseResult result = jobController.commit(map);
        Assert.assertTrue(result.getMessage(), result.getStatus() == 200);
        jobId = ((List<Map>) result.getData()).get(0).get("job_id").toString();

    }

    @Test(priority = 6)
    public void longPollingLongTermResult() {

        DeferredResult<BaseResult> result = jobController.longPollingLongTermResult(jobId);
        BaseResult baseResult = (BaseResult) result.getResult();
        Assert.assertTrue(baseResult.getMessage(), baseResult.getStatus() == 200);

    }

    @Test(priority = 7)
    public void commitReport() {

        HashMap map = jsonToInParameter("parameters/jobTest.json", "report", HashMap.class);
        BaseResult result = jobController.commit(map);
        Assert.assertTrue(result.getMessage(), result.getStatus() == 200);
        jobId = ((List<Map>) result.getData()).get(0).get("job_id").toString();

    }

    @Test(priority = 8)
    public void reportStatus() {

        BaseResult result = jobController.getStatus(jobId);
        Assert.assertTrue(result.getMessage(), result.getStatus() == 200);

    }

    @Test(priority = 9)
    public void commitStatistics() {

        HashMap map = jsonToInParameter("parameters/jobTest.json", "statistics_top", HashMap.class);
        BaseResult result = jobController.commit(map);
        Assert.assertTrue(result.getMessage(), result.getStatus() == 200);
        jobId = ((List<Map>) result.getData()).get(0).get("job_id").toString();

    }

    @Test(priority = 10)
    public void longPollingStatisticsResult() {
        DeferredResult<BaseResult> result = jobController.longPollingFieldDiscoveryResult(jobId);
        BaseResult baseResult = (BaseResult) result.getResult();
        Assert.assertTrue(baseResult.getMessage(), baseResult.getStatus() == 200);

    }

//    @Test(priority = 11)
//    public void getResultReport() {
//        BaseResult result = jobController.getResultReport(jobId);
//        Assert.assertTrue(result.getMessage(), result.getStatus() == 200);
//    }

    @Test(priority = 12)
    public void cancelledJob() {
        BaseResult result = jobController.cancel(jobId);
        Assert.assertTrue(result.getMessage(), result.getStatus() == 200);
    }

}
