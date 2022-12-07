package com.mesalab.qgw.service;

import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.network.dsl.DSLObject;
import com.mesalab.network.service.NetworkMonitorService;
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
        DSLObject dslObject = jsonToInParameter("parameters/networkMonitorTest.json", "appDataSummary", DSLObject.class);
        BaseResult result = networkMonitorService.buildAppData(dslObject);
        Assert.assertTrue(result.getMessage(), result.getStatus() == 200);
    }

    @Test
    public void internalIPDataSummary() {
        DSLObject dslObject = jsonToInParameter("parameters/networkMonitorTest.json", "internalIPDataSummary", DSLObject.class);
        BaseResult result = networkMonitorService.buildInternalIPData(dslObject);
        Assert.assertTrue(result.getMessage(), result.getStatus() == 200);
    }
}
