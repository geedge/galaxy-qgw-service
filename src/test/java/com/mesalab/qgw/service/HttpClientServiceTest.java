package com.mesalab.qgw.service;

import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.qgw.service.impl.HttpClientServiceV2;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

@EnableAutoConfiguration
public class HttpClientServiceTest extends GalaxyQGWApplicationTests {

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private HttpClientServiceV2  httpClientServiceV2;

    @Test
    public void testHttpGet() {
        Assert.assertTrue(httpClientServiceV2.get("https://www.baidu.com", 1000).getStatusCode()==200);
        Assert.assertTrue(httpClientServiceV2.get("http://www.baidu.com", 1).getStatusCode() == ResultStatusEnum.GATEWAY_TIMEOUT.getCode());
        Assert.assertTrue(httpClientServiceV2.get("http://fake.www.baidu.com", 1000).getStatusCode() == ResultStatusEnum.BAD_GATEWAY.getCode());
    }
}
