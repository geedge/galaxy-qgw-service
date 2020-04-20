package com.mesalab.qgw.service;

import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.utils.IPUtil;
import com.zdjizhi.utils.IpLookup;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

@Slf4j
@EnableAutoConfiguration
public class IpLocationTest extends GalaxyQGWApplicationTests {


    @Test
    public void testIpToGeo() {
        log.info(IPUtil.getGeo("157.255.77.80"));
    }

    @Test
    public void testIpToCity() {
        log.info(IPUtil.getCityDetail("157.255.77.80"));

    }

}
