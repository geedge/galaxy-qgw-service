package com.mesalab.qgw.service;

import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.utils.IPUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@EnableAutoConfiguration
public class IpLocationTest extends GalaxyQGWApplicationTests {

    @Test
    public void testIpToGeo() {
        log.info(IPUtil.getInstance().getGeo("157.255.77.80"));
        log.info(IPUtil.getInstance().getGeo("2600:1700:901f:ffff:ffff:ffff:ffff:ffff"));
    }

    @Test
    public void testIpToCity() {
        log.info(IPUtil.getInstance().getCityDetail("157.255.77.80"));

    }

    @Test
    public void testIpToCountry() {
        log.info(IPUtil.getInstance().getCountry("157.255.77.80"));
        log.info(IPUtil.getInstance().getCountry("2600:1700:901f:ffff:ffff:ffff:ffff:ffff"));

    }

    @Test
    public void testIpToISP() {
        log.info(IPUtil.getInstance().getISP("157.255.77.80"));

    }

    @Test
    public void testIpToOrganization() {
        log.info(IPUtil.getInstance().getOrganization("157.255.77.80"));

    }

}
