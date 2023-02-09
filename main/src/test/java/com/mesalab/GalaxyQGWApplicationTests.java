package com.mesalab;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson.JSON;
import com.mesalab.main.GalaxyQGWApplication;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {GalaxyQGWApplication.class}, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@EnableAutoConfiguration
@ActiveProfiles("test")
public class GalaxyQGWApplicationTests extends AbstractTestNGSpringContextTests {
    private static final Log log = LogFactory.get();

    static {
        System.setProperty("jasypt.encryptor.password", "galaxy");
        System.setProperty("JM.SNAPSHOT.PATH", "config");
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
        try {
            json = JSON.parseObject(resourceAsStream, Map.class);
        } catch (IOException e) {
            log.error("读取测试资源目录下的文件失败, 文件名: {}", path);
            throw new RuntimeException(e);
        }
        Object data = json.get(parameterName);
        String objectJsonStr = JSON.toJSONString(data, true);
        log.info("parameter file: {}, parameter name: {}", path, parameterName);
        return JSON.parseObject(objectJsonStr, type);
    }
}
