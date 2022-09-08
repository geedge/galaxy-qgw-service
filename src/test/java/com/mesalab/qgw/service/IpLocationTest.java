package com.mesalab.qgw.service;

import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.utils.IPUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

@Slf4j
@EnableAutoConfiguration
public class IpLocationTest extends GalaxyQGWApplicationTests {

    IPUtil ipUtil = IPUtil.getInstance();
    @Test
    public void testIpToGeo() {
        log.info(ipUtil.getGeo("157.255.77.80"));
        log.info(ipUtil.getGeo("2600:1700:901f:ffff:ffff:ffff:ffff:ffff"));
    }

    @Test
    public void testIpToCity() {
        log.info(ipUtil.getCityDetail("157.255.77.80"));

    }

    @Test
    public void testIpToCountry() {
        log.info(ipUtil.getCountry("157.255.77.80"));
        log.info(ipUtil.getCountry("2600:1700:901f:ffff:ffff:ffff:ffff:ffff"));

    }

    @Test
    public void testIpToISP() {
        log.info(ipUtil.getISP("157.255.77.80"));

    }

    @Test
    public void testIpToOrganization() {
        log.info(ipUtil.getOrganization("157.255.77.80"));

    }

}
