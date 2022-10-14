package com.mesalab.qgw.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.services.service.EntityService;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

@EnableAutoConfiguration
public class TopEntityTest extends GalaxyQGWApplicationTests {
    private static final Log log = LogFactory.get();

    @Autowired
    EntityService entityService;

    private List<Map<String, Object>> getEntityData(String fileName) {
        File file = new File("benchmark" + File.separator + "entity_dataset" + File.separator + fileName);
        List<String> list = FileUtil.readLines(file, CharsetUtil.UTF_8);
        List<Map<String, Object>> data = Lists.newArrayList();
        for (String s : list) {
            String[] split = StrUtil.split(s, "\t");
            Map<String, Object> map = new HashMap<>(16);
            if (split.length >= 2) {
                map.put("sessions", split[1]);
            }
            if (split.length >= 1) {
                map.put("ssl_sni", split[0]);
                data.add(map);
            }
        }
        return data;
    }

    @Test
    public void testCUCCSNIOutput() {
        Stopwatch stopwatch = Stopwatch.createStarted();
        entityService.mergeSNI(getEntityData("XJ-CUCC-SNI-Top200w.txt"));
        stopwatch.stop();
        long millis = stopwatch.elapsed(MILLISECONDS);
        log.info("time:{} ms", millis);
    }

    @Test
    public void testE21SNIOutput() {
        Stopwatch stopwatch = Stopwatch.createStarted();
        entityService.mergeSNI(getEntityData("E21-SNI-Top200w.txt"));
        stopwatch.stop();
        long millis = stopwatch.elapsed(MILLISECONDS);
        log.info("time:{} ms", millis);
    }

    @Test
    public void testE21SNI20221011Output() {
        Stopwatch stopwatch = Stopwatch.createStarted();
        entityService.mergeSNI(getEntityData("E21-SNI-Top3W-20221011.txt"));
        stopwatch.stop();
        long millis = stopwatch.elapsed(MILLISECONDS);
        log.info("time:{} ms", millis);
    }



}
