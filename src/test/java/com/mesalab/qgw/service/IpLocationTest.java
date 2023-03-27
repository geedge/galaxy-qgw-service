package com.mesalab.qgw.service;

import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.utils.IPUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;


@Slf4j
@EnableAutoConfiguration
public class IpLocationTest extends GalaxyQGWApplicationTests {

    @Test
    public void testIpToGeo() {
        log.info(IPUtil.getGeo("157.255.77.80"));
        log.info(IPUtil.getGeo("2600:1700:901f:ffff:ffff:ffff:ffff:ffff"));
    }

    @Test
    public void testIpToCity() {
        log.info(IPUtil.getCityDetail("157.255.77.80"));

    }

    @Test
    public void testIpToCountry() {
        log.info(IPUtil.getCountry("157.255.77.80"));
        log.info(IPUtil.getCountry("2600:1700:901f:ffff:ffff:ffff:ffff:ffff"));

    }

    @Test
    public void testIpToISP() {
        log.info(IPUtil.getISP("157.255.77.80"));

    }

    @Test
    public void testIpToOrganization() {
        log.info(IPUtil.getOrganization("157.255.77.80"));

    }

}
