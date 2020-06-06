package com.mesalab.qgw.service;

import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.zdjizhi.utils.JsonMapper;
import com.zdjizhi.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.JsonProperties;
import org.apache.avro.Schema;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

import java.io.File;
import java.util.*;

@Slf4j
@EnableAutoConfiguration
public class AvroSchemaFormatTest extends GalaxyQGWApplicationTests {
    @Before
    public void testBefore() {
        log.info("=================================================SCHEMA TEST BEGIN=================================================");
    }

    @After
    public void testAfter() {
        log.info("=================================================SCHEMA TEST END=================================================");
    }

    /**
     * 测试druid avro schema
     */
    @Test
    public void checkDruidSchema() {
        checkSchema("/config/avro/druid");
        System.out.println("druid schema format 检测完成");
    }

    /**
     * 测试clickhouse avro schema
     */
    @Test
    public void checkCkSchema() {
        checkSchema("/config/avro/clickhouse");
        System.out.println("clickhouse schema format 检测完成");
    }

    /**
     * 核对avro文件
     *
     * @param path 路径
     */
    public void checkSchema(String path) {
        Map<String, JsonProperties> ckSchemaInfo = getSchemaInfo(path);
        Set<String> name = ckSchemaInfo.keySet();
        Iterator<String> iterator = name.iterator();
        while (iterator.hasNext()) {//遍历表
            String tableName = iterator.next();
            Map resultMap = (Map) JsonMapper.fromJsonString(String.valueOf(ckSchemaInfo.get(tableName)), Map.class);
            if (resultMap == null) {
                throw new BusinessException(ResultStatusEnum.FAIL.getCode(), "schema未注册此表: " + tableName, null);
            }
            List<Map> fields = (List<Map>) resultMap.get("fields");//遍历列
            Iterator<Map> fieldsIterator = fields.iterator();
            Map mapDoc = null;
            while (fieldsIterator.hasNext()) {
                Map next = fieldsIterator.next();
                try {
                    mapDoc = (Map) JsonMapper.fromJsonString(String.valueOf(next.get("doc")), Map.class);
                } catch (Exception ex) {
                    log.error("该字段的 doc 为非JSON字符串，不需类型转换");
                }
                if (mapDoc != null) {
                    next.put("doc", mapDoc);
                }
                if (StringUtil.isEmpty(mapDoc) && !StringUtil.isEmpty(next.get("doc"))) {
                    log.error("该字段的 doc 为非JSON字符串，不需类型转换");
                    throw new RuntimeException(next.get("name") + "此字段doc非json格式", null);
                }
            }
        }
    }

    /**
     * 获取avro文件
     *
     * @param path
     * @return
     */
    public static Map<String, JsonProperties> getSchemaInfo(String path) {
        Map<String, JsonProperties> cKSchema = new HashMap<>();
        String pathRoot = null;
        try {
            pathRoot = new File("").getCanonicalPath();
            File file = new File(pathRoot + path);
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    if(!f.getName().toLowerCase().endsWith(".avsc")){
                        continue;
                    }
                    try {
                        Schema ckSchema = new Schema.Parser().parse(new File(f.getPath()));
                        cKSchema.put(ckSchema.getName(), ckSchema);
                    } catch (Exception ex) {
                        log.error("遍历" + path + "路径下文件失败: " + f.getName(), ex);
                        throw new RuntimeException(String.format("遍历%s 路径下文件%s失败: %s", path, f.getName()), ex);
                    }
                }
            }
        } catch (Exception e) {
            log.error("获取 项目路径下文件失败", path);
            throw new RuntimeException(String.format("遍历%s 路径下文件失败", path));
        }
        return cKSchema;
    }
}
