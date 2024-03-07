package com.mesalab.qgw.service;


import cn.hutool.core.io.resource.ClassPathResource;
import cn.hutool.core.io.resource.Resource;
import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.utils.JsonSchemaValidator;
import com.mesalab.qgw.exception.QGWBusinessException;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.test.context.TestPropertySource;
import java.nio.charset.StandardCharsets;
import java.util.Map;


@EnableAutoConfiguration
@TestPropertySource("classpath:")
public class JsonSchemaValidatorTest extends GalaxyQGWApplicationTests {
    @Autowired
    DatabaseService databaseService;

    @Test
    public void testJsonSchema() {
        try {
            JsonSchemaValidator jsonSchemaValidator = JsonSchemaValidator.getInstance();
            Map<String, Object> schemaMap = databaseService.getSchemaInfo("fields", "session_record", false);
            boolean isValidSuccess = jsonSchemaValidator
                    .addRule("schema-syntax-validation.json")
                    .validate(com.alibaba.fastjson2.JSON.toJSONString(schemaMap));
            Assert.assertTrue(isValidSuccess);
        } catch (QGWBusinessException e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testInvalidJsonSchema() {
        boolean isValidError = true;
        try {
            JsonSchemaValidator jsonSchemaValidator = JsonSchemaValidator.getInstance();
            Resource resource = new ClassPathResource("examples/invalid_session_record_test.json");
            String jsonStr = new String(IOUtils.toByteArray(resource.getStream()), StandardCharsets.UTF_8);
            jsonSchemaValidator
                    .addRule("schema-syntax-validation.json")
                    .validate(jsonStr);
        } catch (Exception e) {
            isValidError = false;
        }
        Assert.assertFalse(isValidError);
    }
}
