package com.mesalab.qgw.service;

import cn.hutool.core.io.file.FileReader;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.base.Stopwatch;
import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.network.service.NetworkMonitorService;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

import java.io.File;
import java.util.*;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

@EnableAutoConfiguration
public class ProtocolTreeTest extends GalaxyQGWApplicationTests {
    private static final Log log = LogFactory.get();
    @Autowired
    public NetworkMonitorService networkMonitorService;

    private List<Map> getLiveChartsData(String fileName) {
        File file = new File("benchmark" + File.separator + "live_traffic_chart" + File.separator + fileName);
        Map resultMap = (Map) JsonMapper.fromJsonString(FileReader.create(file).readString(), Map.class);
        List<Map> resultList = (List<Map>) resultMap.get("data");
        return resultList;
    }

    @Test
    public void test5MinuteTreeOutput() {
        Stopwatch stopwatch = Stopwatch.createStarted();
        networkMonitorService.buildHierarchicalStructure(networkMonitorService.buildFlatStructure(getLiveChartsData("livecharts-5min.txt")));
        stopwatch.stop();
        long millis = stopwatch.elapsed(MILLISECONDS);
        log.info("time:{} ms", millis);
    }

    @Test
    public void test10MinuteTreeOutput() {
        Stopwatch stopwatch = Stopwatch.createStarted();
        networkMonitorService.buildHierarchicalStructure(networkMonitorService.buildFlatStructure(getLiveChartsData("livecharts-10min.txt")));
        stopwatch.stop();
        long millis = stopwatch.elapsed(MILLISECONDS);
        log.info("time:{} ms", millis);
    }
    @Test
    public void test30MinuteTreeOutput() {
        Stopwatch stopwatch = Stopwatch.createStarted();
        networkMonitorService.buildHierarchicalStructure(networkMonitorService.buildFlatStructure(getLiveChartsData("livecharts-30min.txt")));
        stopwatch.stop();
        long millis = stopwatch.elapsed(MILLISECONDS);
        log.info("time:{} ms", millis);
    }
    @Test
    public void test1HourTreeOutput() {
        Stopwatch stopwatch = Stopwatch.createStarted();
        networkMonitorService.buildHierarchicalStructure(networkMonitorService.buildFlatStructure(getLiveChartsData("livecharts-1h.txt")));
        stopwatch.stop();
        long millis = stopwatch.elapsed(MILLISECONDS);
        log.info("time:{} ms", millis);
    }

}
