package com.mesalab.qgw.service;


import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson2.JSON;
import com.google.common.collect.Maps;
import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.utils.JsonSchemaValidator;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.qgw.exception.QGWBusinessException;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

import java.util.List;
import java.util.Map;


@EnableAutoConfiguration
public class JsonSchemaValidatorTest extends GalaxyQGWApplicationTests {
    private static final Log log = LogFactory.get();

    @Autowired
    DatabaseService databaseService;

    @Test
    public void testJsonSchemaValidator() {
        try {
            JsonSchemaValidator jsonSchemaValidator = JsonSchemaValidator.getInstance();
            Map schemaMap = databaseService.getSchemaInfo("fields", "session_record", false);
            List<String> validate = jsonSchemaValidator
                    .addRule("rules/fieldUniquenessRuleTest.json", QGWMessageConst.JSON_SCHEMA_FIELD_UNIQUENESS, "WARN")
                    .addRule("rules/docFieldUsabilityRuleTest.json", QGWMessageConst.JSON_SCHEMA_DOC_FIELD_USABILITY, "ERROR")
                    .addRule("rules/attributeNecessityRuleTest.json", QGWMessageConst.JSON_SCHEMA_ATTRIBUTE_NECESSITY, "ERROR")
                    .validate(com.alibaba.fastjson2.JSON.toJSONString(schemaMap));
            if (!validate.isEmpty()) {
                for (String message : validate) {
                    log.warn(message);
                }
            }
        } catch (QGWBusinessException e) {
            Assert.fail(e.getMessage());
        }
    }
}
