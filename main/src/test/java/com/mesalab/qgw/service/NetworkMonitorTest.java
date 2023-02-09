package com.mesalab.qgw.service;

import com.mesalab.tsg.entity.NetWorkDSLObject;
import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.tsg.service.NetworkMonitorService;
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
        NetWorkDSLObject dslObject = jsonToInParameter("parameters/networkMonitorTest.json", "appDataRateSummary", NetWorkDSLObject.class);
        BaseResult result = networkMonitorService.buildAppDataRate(dslObject);
        Assert.assertTrue(result.getMessage(), result.getStatus() == 200);
    }

    @Test
    public void appTrafficSummary() {
        NetWorkDSLObject dslObject = jsonToInParameter("parameters/networkMonitorTest.json", "appTrafficSummary", NetWorkDSLObject.class);
        BaseResult result = networkMonitorService.buildAppTraffic(dslObject);
        Assert.assertTrue(result.getMessage(), result.getStatus() == 200);
    }
}
