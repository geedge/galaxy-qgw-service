package com.mesalab.qgw.service;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.zdjizhi.utils.JsonMapper;
import com.zdjizhi.utils.StringUtil;
import org.apache.avro.JsonProperties;
import org.apache.avro.Schema;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

@EnableAutoConfiguration
public class AvroSchemaFormatTest extends GalaxyQGWApplicationTests {
    private static final Log log = LogFactory.get();
    private static final String CK_SCHEMA_DIR = File.separator + "config"
            + File.separator + "avro" +  File.separator + "clickhouse";
    private static final String DRUID_SCHEMA_DIR = File.separator + "config"
            + File.separator + "avro" +  File.separator + "druid";
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
        checkSchema(DRUID_SCHEMA_DIR);
        log.info("druid schema format 检测完成");
    }

    /**
     * 测试clickhouse avro schema
     */
    @Test
    public void checkCkSchema() {
        checkSchema(CK_SCHEMA_DIR);
        log.info("clickhouse schema format 检测完成");
    }

    /**
     * 核对avro文件
     *
     * @param path 路径
     */
    private void checkSchema(String path) {
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
    private static Map<String, JsonProperties> getSchemaInfo(String path) {
        Map<String, JsonProperties> cKSchema = new HashMap<>();
        String pathRoot = null;
        try {
            pathRoot = new File("").getCanonicalPath();
            File file = new File(pathRoot + path);
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    if(!StringUtils.lowerCase(f.getName()).endsWith(".avsc")){
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
        } catch (IOException e) {
            log.error("获取 项目路径下文件失败", path);
        }
        return cKSchema;
    }
}
