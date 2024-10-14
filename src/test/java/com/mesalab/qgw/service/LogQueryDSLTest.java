package com.mesalab.qgw.service;

import com.alibaba.fastjson2.JSON;
import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.enums.ExecutionMode;
import com.mesalab.qgw.controller.QueryController;
import com.mesalab.qgw.model.basic.DSLQueryRequestParam;
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
public class LogQueryDSLTest extends GalaxyQGWApplicationTests {

    @Autowired
    private DatasetService datasetService;
    @Autowired
    private QueryJobService queryJobService;

    @Test
    public void logQueryDSLTest() {
        Map<String, Object> dataset = datasetService.getDataset("log-query");
        String template = datasetService.buildExecDSL(datasetService.getVariable(), String.valueOf(dataset.get("template")));
        DSLQueryRequestParam dslQueryRequestParam = JSON.parseObject(template, DSLQueryRequestParam.class);
        dslQueryRequestParam.setExecutionMode(ExecutionMode.NORMAL.getValue());
        BaseResult sqlAdHocQuery = queryJobService.createDSLAdHocQuery(dslQueryRequestParam);
        String jobId = String.valueOf(sqlAdHocQuery.getJob().get("job_id"));
        BaseResult adHocQueryStatusById = queryJobService.getAdHocQueryStatusById(jobId);
        while (!Boolean.parseBoolean(String.valueOf(adHocQueryStatusById.getJob().get("is_done")))) {
            BaseResult adHocQueryTimelineById = queryJobService.getAdHocQueryTimelineById(jobId);
            BaseResult adHocQueryListById = queryJobService.getAdHocQueryListById(jobId, "*", 1, 1);
            BaseResult adHocQueryCountById = queryJobService.getAdHocQueryCountById(jobId);

            Assert.assertEquals(adHocQueryTimelineById.getMessage(), 200, (int) adHocQueryTimelineById.getStatus());
            Assert.assertEquals(adHocQueryListById.getMessage(), 200, (int) adHocQueryListById.getStatus());
            Assert.assertEquals(adHocQueryCountById.getMessage(), 200, (int) adHocQueryCountById.getStatus());
            BaseResult adHocQueryStatusByIdTemp = queryJobService.getAdHocQueryStatusById(jobId);
            if (Boolean.parseBoolean(String.valueOf(adHocQueryStatusByIdTemp.getJob().get("is_done")))) {
                break;
            }

        }
        BaseResult adHocQueryStatusByIdEnd = queryJobService.getAdHocQueryStatusById(jobId);
        BaseResult adHocQueryTimelineByIdEnd = queryJobService.getAdHocQueryTimelineById(jobId);
        BaseResult adHocQueryListByIdEnd = queryJobService.getAdHocQueryListById(jobId, "*", 1, 1);
        BaseResult adHocQueryCountByIdEnd = queryJobService.getAdHocQueryCountById(jobId);
        Assert.assertEquals(adHocQueryTimelineByIdEnd.getMessage(), 200, (int) adHocQueryTimelineByIdEnd.getStatus());
        Assert.assertEquals(adHocQueryListByIdEnd.getMessage(), 200, (int) adHocQueryListByIdEnd.getStatus());
        Assert.assertEquals(adHocQueryCountByIdEnd.getMessage(), 200, (int) adHocQueryCountByIdEnd.getStatus());
        boolean isFailed = Boolean.parseBoolean(String.valueOf(adHocQueryStatusByIdEnd.getJob().get("is_failed")));
        boolean isCanceled = Boolean.parseBoolean(String.valueOf(adHocQueryStatusByIdEnd.getJob().get("is_canceled")));
        Assert.assertFalse(String.valueOf(adHocQueryStatusByIdEnd.getJob().get("reason")), isFailed);
        Assert.assertFalse(String.valueOf(adHocQueryStatusByIdEnd.getJob().get("reason")), isCanceled);

    }

}
