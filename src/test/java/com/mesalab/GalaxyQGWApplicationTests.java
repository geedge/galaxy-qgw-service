package com.mesalab;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson2.JSON;
import com.geedgenetworks.utils.StringUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {GalaxyQGWApplication.class}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableAutoConfiguration
@ActiveProfiles("test")
public class GalaxyQGWApplicationTests {
    private static final Log log = LogFactory.get();
    private static final String TIMEZONE = "UTC";

    @LocalServerPort
    protected int testPort;

    static {
        System.setProperty("jasypt.encryptor.password", "galaxy");
        System.setProperty("JM.SNAPSHOT.PATH", "config");
        RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
        List<String> inputArguments = runtimeMxBean.getInputArguments();
        if (inputArguments.stream().noneMatch(o -> o.contains("user.timezone"))
                && StringUtil.isEmpty(System.getenv().get("TZ"))) {
            // java.util.Date
            TimeZone.setDefault(TimeZone.getTimeZone(TIMEZONE));
            // joda.time.DateTime
            System.setProperty("user.timezone", TIMEZONE);
        }
    }

    @Before
    public void testBefore() {
        log.info("=================================================GALAXY QUERY GATEWAY BEGIN=================================================");

    }

    @Test
    public void contextLoads() {
        log.info("GALAXY QUERY GATEWAY CONTEXT LOAD");
    }

    @After
    public void testAfter() {
        log.info("=================================================GALAXY QUERY GATEWAY END=================================================");

    }

    // 解析resource: parameters/otherPath/指定JSON文件, 返回指定类型对象
    public <T> T jsonToInParameter(String path, String parameterName,Class<T> type) {
        InputStream resourceAsStream = GalaxyQGWApplicationTests.class.getClassLoader().getResourceAsStream(path);
        if (Objects.isNull(resourceAsStream)) {
            log.info("获取不到 [{}] 路径下的指定文件, 请检查文件是否存在/文件名大小写是否一致", path);
            throw new IllegalArgumentException();
        }
        Map<String, Object> json = null;
        json = JSON.parseObject(resourceAsStream, Map.class);
        Object data = json.get(parameterName);
        String objectJsonStr = JSON.toJSONString(data);
        log.info("parameter file: {}, parameter name: {}", path, parameterName);
        return JSON.parseObject(objectJsonStr, type);
    }
}
