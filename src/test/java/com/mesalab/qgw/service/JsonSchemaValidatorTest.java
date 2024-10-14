package com.mesalab.qgw.service;


import cn.hutool.core.io.resource.ClassPathResource;
import cn.hutool.core.io.resource.Resource;
import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.enums.DatasetType;
import com.mesalab.common.utils.JsonSchemaValidator;
import com.mesalab.qgw.exception.QGWBusinessException;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
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
                    .addRule("schema-syntax-validation.json", "schema")
                    .validateSchema(com.alibaba.fastjson2.JSON.toJSONString(schemaMap));
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
            Resource resource = new ClassPathResource("examples/invalidSessionRecordTest.json");
            String jsonStr = new String(IOUtils.toByteArray(resource.getStream()), StandardCharsets.UTF_8);
            jsonSchemaValidator
                    .addRule("schema-syntax-validation.json", "schema")
                    .validateSchema(jsonStr);
        } catch (Exception e) {
            isValidError = false;
        }
        Assert.assertFalse(isValidError);
    }

    @Test
    public void testDSLRequest() {
        try {
            JsonSchemaValidator jsonSchemaValidator = JsonSchemaValidator.getInstance();
            Resource resource = new ClassPathResource("examples/validDSLRequestTest.json");
            String jsonStr = new String(IOUtils.toByteArray(resource.getStream()), StandardCharsets.UTF_8);
            boolean isValidSuccess = jsonSchemaValidator
                    .addRule("dsl-validation.json", DatasetType.DSL.getValue())
                    .validateDSL(jsonStr);
            Assert.assertTrue(isValidSuccess);
        } catch (QGWBusinessException | IOException e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testInvalidDSLRequest() {
        boolean isValidError = true;
        try {
            JsonSchemaValidator jsonSchemaValidator = JsonSchemaValidator.getInstance();
            Resource resource = new ClassPathResource("examples/invalidDSLRequestTest.json");
            String jsonStr = new String(IOUtils.toByteArray(resource.getStream()), StandardCharsets.UTF_8);
            jsonSchemaValidator
                    .addRule("dsl-validation.json", DatasetType.DSL.getValue())
                    .validateDSL(jsonStr);
        } catch (Exception e) {
            isValidError = false;
        }
        Assert.assertFalse(isValidError);
    }
}
