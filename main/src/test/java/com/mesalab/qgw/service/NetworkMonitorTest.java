package com.mesalab.qgw.service;

import com.measlab.tsg.entity.NetWorkDSLObject;
import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.network.dsl.DSLObject;
import com.measlab.tsg.service.NetworkMonitorService;
import org.junit.Assert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.testng.annotations.Test;

/**
 * TODO
 *
 * @Classname NetworkMonitorTest
 * @Date 2022/12/7 09:45
 * @Author wWei
 */
@EnableAutoConfiguration
public class NetworkMonitorTest extends GalaxyQGWApplicationTests {

    @Autowired
    NetworkMonitorService networkMonitorService;

    @Test
    public void appDataSummary() {
        NetWorkDSLObject dslObject = jsonToInParameter("parameters/networkMonitorTest.json", "appDataSummary", NetWorkDSLObject.class);
        BaseResult result = networkMonitorService.buildAppData(dslObject);
        Assert.assertTrue(result.getMessage(), result.getStatus() == 200);
    }

    @Test
    public void internalIPDataSummary() {
        NetWorkDSLObject dslObject = jsonToInParameter("parameters/networkMonitorTest.json", "internalIPDataSummary", NetWorkDSLObject.class);
        BaseResult result = networkMonitorService.buildInternalIPData(dslObject);
        Assert.assertTrue(result.getMessage(), result.getStatus() == 200);
    }

    @Test
    public void appDataRateSummary() {
        DSLObject dslObject = jsonToInParameter("parameters/networkMonitorTest.json", "appDataRateSummary", DSLObject.class);
        BaseResult result = networkMonitorService.buildAppDataRate(dslObject);
        Assert.assertTrue(result.getMessage(), result.getStatus() == 200);
    }

    @Test
    public void appTrafficSummary() {
        DSLObject dslObject = jsonToInParameter("parameters/networkMonitorTest.json", "appTrafficSummary", DSLObject.class);
        BaseResult result = networkMonitorService.buildAppTraffic(dslObject);
        Assert.assertTrue(result.getMessage(), result.getStatus() == 200);
    }
}
