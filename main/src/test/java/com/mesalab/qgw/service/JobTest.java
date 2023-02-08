package com.mesalab.qgw.service;

import com.measlab.tsg.controller.JobController;
import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.entity.BaseResult;
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
    public void commitSavedQuery() {

        HashMap map = jsonToInParameter("parameters/jobTest.json", "jobTest", HashMap.class);
        BaseResult result = jobController.commitJob(map);
        Assert.assertTrue(result.getMessage(), result.getStatus() == 200);
        jobId = ((List<Map>) result.getData()).get(0).get("jobId").toString();

    }

    @Test(priority = 2)
    public void longPollingFieldDiscoveryResult() {

        DeferredResult<BaseResult> result = jobController.longPollingFieldDiscoveryResult(jobId);
        BaseResult baseResult = (BaseResult) result.getResult();
        Assert.assertTrue(baseResult.getMessage(), baseResult.getStatus() == 200);

    }
    @Test(priority = 3)
    public void longPollingStatisticsResult() {

        DeferredResult<BaseResult> result = jobController.longPollingStatisticsResult(jobId);
        BaseResult baseResult = (BaseResult) result.getResult();
        Assert.assertTrue(baseResult.getMessage(), baseResult.getStatus() == 200);

    }

//    @Test(priority = 4)
//    public void getResultReport() {
//        BaseResult result = jobController.getResultReport(jobId);
//        Assert.assertTrue(result.getMessage(), result.getStatus() == 200);
//    }

    @Test(priority = 5)
    public void getStatus() {
        BaseResult result = jobController.getStatus(jobId);
        Assert.assertTrue(result.getMessage(), result.getStatus() == 200);
    }

    @Test(priority = 6)
    public void cancelledJob() {
        BaseResult result = jobController.cancelledJob(jobId);
        Assert.assertTrue(result.getMessage(), result.getStatus() == 200);
    }
}
