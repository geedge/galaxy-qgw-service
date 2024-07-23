package com.mesalab.qgw.service;

import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.qgw.controller.QueryController;
import com.mesalab.qgw.model.basic.DSLQueryRequestParam;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;


/**
 * TODO
 *
 * @Classname CustomizedStatisticsTest
 * @Date 2024/7/22 18:35
 * @Author wWei
 */
@EnableAutoConfiguration
public class CustomizedStatisticsTest extends GalaxyQGWApplicationTests {

    @Autowired
    private QueryController queryController;

    @Test
    public void testMetricsValueStatistics() {
        DSLQueryRequestParam body = jsonToInParameter("parameters/customizedStatisticsTest.json", "metrics_value_statistics", DSLQueryRequestParam.class);
        BaseResult commitResult = queryController.createDSLQuery(body);
        Assert.assertTrue(commitResult.getMessage(), commitResult.getStatus() == 200);
    }

    @Test
    public void testMetricsValueStatisticsOverTime() {
        DSLQueryRequestParam body = jsonToInParameter("parameters/customizedStatisticsTest.json", "metrics_value_statistics_over_time", DSLQueryRequestParam.class);
        BaseResult commitResult = queryController.createDSLQuery(body);
        Assert.assertTrue(commitResult.getMessage(), commitResult.getStatus() == 200);
    }

    @Test
    public void testMetricsValueSeriesStatisticsOverTime() {
        DSLQueryRequestParam body = jsonToInParameter("parameters/customizedStatisticsTest.json", "metrics_value_series_statistics_over_time", DSLQueryRequestParam.class);
        BaseResult commitResult = queryController.createDSLQuery(body);
        Assert.assertTrue(commitResult.getMessage(), commitResult.getStatus() == 200);
    }
}
