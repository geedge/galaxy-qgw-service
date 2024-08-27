package com.mesalab.qgw.service;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.collect.Lists;
import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.utils.sqlparser.AutoPeriodHelper;
import com.mesalab.qgw.model.basic.DSLQueryRequestParam;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * TODO
 *
 * @Classname AutoPeriodTest
 * @Date 2024/6/20 09:24
 * @Author wWei
 */
@EnableAutoConfiguration
public class AutoPeriodDSLTest extends GalaxyQGWApplicationTests {
    private static final Log log = LogFactory.get();
    private static final DateTime now;

    static {
        now = new DateTime();
    }

    @Test
    public void testDSLLast5Minutes() {
        DSLQueryRequestParam dslQueryRequestParam = jsonToInParameter("parameters/dslAutoGranularityTest.json", "application_and_protocol_summary_auto", DSLQueryRequestParam.class);
        String endTime = DateUtil.format(now, "yyyy-MM-dd HH:mm:ss");
        String startTime = DateUtil.format(DateUtil.offsetMinute(now, -5), "yyyy-MM-dd HH:mm:ss");
        dslQueryRequestParam.setIntervals(Lists.newArrayList(startTime + "/" + endTime));
        AutoPeriodHelper.buildDslGranularity(dslQueryRequestParam);
        String granularity = dslQueryRequestParam.getGranularity();
        log.info(granularity);
        Assert.assertEquals("ERROR: don't expect period", "PT1S", granularity);
    }

    @Test
    public void testDSLLast5MinutesISO8601() {
        DSLQueryRequestParam dslQueryRequestParam = jsonToInParameter("parameters/dslAutoGranularityTest.json", "application_and_protocol_summary_auto", DSLQueryRequestParam.class);
        String endTime = DateUtil.format(now, "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        String startTime = DateUtil.format(DateUtil.offsetMinute(now, -5), "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        dslQueryRequestParam.setIntervals(Lists.newArrayList(startTime + "/" + endTime));
        AutoPeriodHelper.buildDslGranularity(dslQueryRequestParam);
        String granularity = dslQueryRequestParam.getGranularity();
        log.info(granularity);
        Assert.assertEquals("ERROR: don't expect period", "PT1S", granularity);
    }

    @Test
    public void testDSLLast30Minutes() {
        DSLQueryRequestParam dslQueryRequestParam = jsonToInParameter("parameters/dslAutoGranularityTest.json", "application_and_protocol_summary_auto", DSLQueryRequestParam.class);
        String endTime = DateUtil.format(now, "yyyy-MM-dd HH:mm:ss");
        String startTime = DateUtil.format(DateUtil.offsetMinute(now, -30), "yyyy-MM-dd HH:mm:ss");
        dslQueryRequestParam.setIntervals(Lists.newArrayList(startTime + "/" + endTime));
        AutoPeriodHelper.buildDslGranularity(dslQueryRequestParam);
        String granularity = dslQueryRequestParam.getGranularity();
        log.info(granularity);
        Assert.assertEquals("ERROR: don't expect period", "PT30S", granularity);
    }

    @Test
    public void testDSLLast1Hour() {
        DSLQueryRequestParam dslQueryRequestParam = jsonToInParameter("parameters/dslAutoGranularityTest.json", "application_and_protocol_summary_auto", DSLQueryRequestParam.class);
        String endTime = DateUtil.format(now, "yyyy-MM-dd HH:mm:ss");
        String startTime = DateUtil.format(DateUtil.offsetHour(now, -1), "yyyy-MM-dd HH:mm:ss");
        dslQueryRequestParam.setIntervals(Lists.newArrayList(startTime + "/" + endTime));
        AutoPeriodHelper.buildDslGranularity(dslQueryRequestParam);
        String granularity = dslQueryRequestParam.getGranularity();
        log.info(granularity);
        Assert.assertEquals("ERROR: don't expect period", "PT30S", granularity);
    }

    @Test
    public void testDSLLast6Hour() {
        DSLQueryRequestParam dslQueryRequestParam = jsonToInParameter("parameters/dslAutoGranularityTest.json", "application_and_protocol_summary_auto", DSLQueryRequestParam.class);
        String endTime = DateUtil.format(now, "yyyy-MM-dd HH:mm:ss");
        String startTime = DateUtil.format(DateUtil.offsetHour(now, -6), "yyyy-MM-dd HH:mm:ss");
        dslQueryRequestParam.setIntervals(Lists.newArrayList(startTime + "/" + endTime));
        AutoPeriodHelper.buildDslGranularity(dslQueryRequestParam);
        String granularity = dslQueryRequestParam.getGranularity();
        log.info(granularity);
        Assert.assertEquals("ERROR: don't expect period", "PT1M", granularity);
    }

    @Test
    public void testDSLLast12Hour() {
        DSLQueryRequestParam dslQueryRequestParam = jsonToInParameter("parameters/dslAutoGranularityTest.json", "application_and_protocol_summary_auto", DSLQueryRequestParam.class);
        String endTime = DateUtil.format(now, "yyyy-MM-dd HH:mm:ss");
        String startTime = DateUtil.format(DateUtil.offsetHour(now, -12), "yyyy-MM-dd HH:mm:ss");
        dslQueryRequestParam.setIntervals(Lists.newArrayList(startTime + "/" + endTime));
        AutoPeriodHelper.buildDslGranularity(dslQueryRequestParam);
        String granularity = dslQueryRequestParam.getGranularity();
        log.info(granularity);
        Assert.assertEquals("ERROR: don't expect period", "PT1M", granularity);
    }

    @Test
    public void testDSLLast1Day() {
        DSLQueryRequestParam dslQueryRequestParam = jsonToInParameter("parameters/dslAutoGranularityTest.json", "application_and_protocol_summary_auto", DSLQueryRequestParam.class);
        String endTime = DateUtil.format(now, "yyyy-MM-dd HH:mm:ss");
        String startTime = DateUtil.format(DateUtil.offsetDay(now, -1), "yyyy-MM-dd HH:mm:ss");
        dslQueryRequestParam.setIntervals(Lists.newArrayList(startTime + "/" + endTime));
        AutoPeriodHelper.buildDslGranularity(dslQueryRequestParam);
        String granularity = dslQueryRequestParam.getGranularity();
        log.info(granularity);
        Assert.assertEquals("ERROR: don't expect period", "PT5M", granularity);
    }

    @Test
    public void testDSLLast2Day() {
        DSLQueryRequestParam dslQueryRequestParam = jsonToInParameter("parameters/dslAutoGranularityTest.json", "application_and_protocol_summary_auto", DSLQueryRequestParam.class);
        String endTime = DateUtil.format(now, "yyyy-MM-dd HH:mm:ss");
        String startTime = DateUtil.format(DateUtil.offsetDay(now, -2), "yyyy-MM-dd HH:mm:ss");
        dslQueryRequestParam.setIntervals(Lists.newArrayList(startTime + "/" + endTime));
        AutoPeriodHelper.buildDslGranularity(dslQueryRequestParam);
        String granularity = dslQueryRequestParam.getGranularity();
        log.info(granularity);
        Assert.assertEquals("ERROR: don't expect period", "PT5M", granularity);
    }

    @Test
    public void testDSLLast3Day() {
        DSLQueryRequestParam dslQueryRequestParam = jsonToInParameter("parameters/dslAutoGranularityTest.json", "application_and_protocol_summary_auto", DSLQueryRequestParam.class);
        String endTime = DateUtil.format(now, "yyyy-MM-dd HH:mm:ss");
        String startTime = DateUtil.format(DateUtil.offsetDay(now, -3), "yyyy-MM-dd HH:mm:ss");
        dslQueryRequestParam.setIntervals(Lists.newArrayList(startTime + "/" + endTime));
        AutoPeriodHelper.buildDslGranularity(dslQueryRequestParam);
        String granularity = dslQueryRequestParam.getGranularity();
        log.info(granularity);
        Assert.assertEquals("ERROR: don't expect period", "PT5M", granularity);
    }

    @Test
    public void testDSLLast1Week() {
        DSLQueryRequestParam dslQueryRequestParam = jsonToInParameter("parameters/dslAutoGranularityTest.json", "application_and_protocol_summary_auto", DSLQueryRequestParam.class);
        String endTime = DateUtil.format(now, "yyyy-MM-dd HH:mm:ss");
        String startTime = DateUtil.format(DateUtil.offsetWeek(now, -1), "yyyy-MM-dd HH:mm:ss");
        dslQueryRequestParam.setIntervals(Lists.newArrayList(startTime + "/" + endTime));
        AutoPeriodHelper.buildDslGranularity(dslQueryRequestParam);
        String granularity = dslQueryRequestParam.getGranularity();
        log.info(granularity);
        Assert.assertEquals("ERROR: don't expect period", "PT1H", granularity);
    }

    @Test
    public void testDSLLast30Day() {
        DSLQueryRequestParam dslQueryRequestParam = jsonToInParameter("parameters/dslAutoGranularityTest.json", "application_and_protocol_summary_auto", DSLQueryRequestParam.class);
        String endTime = DateUtil.format(now, "yyyy-MM-dd HH:mm:ss");
        String startTime = DateUtil.format(DateUtil.offsetDay(now, -30), "yyyy-MM-dd HH:mm:ss");
        dslQueryRequestParam.setIntervals(Lists.newArrayList(startTime + "/" + endTime));
        AutoPeriodHelper.buildDslGranularity(dslQueryRequestParam);
        String granularity = dslQueryRequestParam.getGranularity();
        log.info(granularity);
        Assert.assertEquals("ERROR: don't expect period", "PT1H", granularity);
    }

    @Test
    public void testDSLLast365Day() {
        DSLQueryRequestParam dslQueryRequestParam = jsonToInParameter("parameters/dslAutoGranularityTest.json", "application_and_protocol_summary_auto", DSLQueryRequestParam.class);
        String endTime = DateUtil.format(now, "yyyy-MM-dd HH:mm:ss");
        String startTime = DateUtil.format(DateUtil.offsetDay(now, -365), "yyyy-MM-dd HH:mm:ss");
        dslQueryRequestParam.setIntervals(Lists.newArrayList(startTime + "/" + endTime));
        AutoPeriodHelper.buildDslGranularity(dslQueryRequestParam);
        String granularity = dslQueryRequestParam.getGranularity();
        log.info(granularity);
        Assert.assertEquals("ERROR: don't expect period", "P1D", granularity);
    }

    @Test
    public void testDSLConst() {
        DSLQueryRequestParam dslQueryRequestParam = jsonToInParameter("parameters/dslAutoGranularityTest.json", "application_and_protocol_summary_const", DSLQueryRequestParam.class);
        AutoPeriodHelper.buildDslGranularity(dslQueryRequestParam);
        String granularity = dslQueryRequestParam.getGranularity();
        log.info(granularity);
        Assert.assertEquals("ERROR: don't expect period", "PT1S", granularity);
    }

    @Test
    public void testDSLAutoRange() {
        DSLQueryRequestParam dslQueryRequestParam = jsonToInParameter("parameters/dslAutoGranularityTest.json", "application_and_protocol_summary_auto_const_range", DSLQueryRequestParam.class);
        AutoPeriodHelper.buildDslGranularity(dslQueryRequestParam);
        String granularity = dslQueryRequestParam.getGranularity();
        log.info(granularity);
        Assert.assertEquals("ERROR: don't expect period", "P1D", granularity);
    }

    @Test
    public void testDSLTrafficSpectrumNetworkThroughputTrend() {
        DSLQueryRequestParam dslQueryRequestParam = jsonToInParameter("parameters/dslAutoGranularityTest.json", "traffic_spectrum_network_throughput_trend_auto", DSLQueryRequestParam.class);
        String endTime = DateUtil.format(now, "yyyy-MM-dd HH:mm:ss");
        String startTime = DateUtil.format(DateUtil.offsetHour(now, -1), "yyyy-MM-dd HH:mm:ss");
        dslQueryRequestParam.setIntervals(Lists.newArrayList(startTime + "/" + endTime));
        AutoPeriodHelper.buildDslGranularity(dslQueryRequestParam);
        String granularity = dslQueryRequestParam.getGranularity();
        log.info(granularity);
        Assert.assertEquals("ERROR: don't expect period", "PT1M", granularity);
    }
}
