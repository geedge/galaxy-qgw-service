package com.mesalab.common.utils;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.io.resource.ClassPathResource;
import cn.hutool.core.io.resource.Resource;
import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.databind.JsonNode;
import com.mesalab.common.enums.HttpStatusCodeEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.networknt.schema.*;
import lombok.Data;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;


public class JsonSchemaValidator {
    private static final Log log = LogFactory.get();
    private static final JsonSchemaValidator INSTANCE = new JsonSchemaValidator();
    private final List<ValidationRule> rules;
    private static final String ENUM_VALUES = "\"$ENUM_VALUES$\"";
    private static final String EXPRESSION_FIELDS = "expression_fields";
    private final JsonSchemaFactory schemaFactory;
    private final SchemaValidatorsConfig schemaValidatorsConfig;

    private JsonSchemaValidator() {
        this.schemaFactory = JsonSchemaFactory
                .builder(com.networknt.schema.JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7))
                .build();
        this.schemaValidatorsConfig = new SchemaValidatorsConfig();
        this.schemaValidatorsConfig.setLocale(Locale.ENGLISH);
        this.rules = new ArrayList<>();
    }

    public static JsonSchemaValidator getInstance() {
        return INSTANCE;
    }

    public JsonSchemaValidator addRule(String jsonSchemaFileName, String type) {
        if (StrUtil.isNotBlank(jsonSchemaFileName) && StrUtil.isNotBlank(type)) {
            ValidationRule rule = new ValidationRule(jsonSchemaFileName, type);
            if (!rules.contains(rule)) {
                rules.add(rule);
            }
            return this;
        } else {
            throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(),
                    CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                    "Json-schema ruleName and type cannot be empty.");
        }
    }


    public boolean validateSchema(String jsonStr) {
        for (ValidationRule rule : rules) {
            if (rule.getType().equals("schema")){
                Map map = JSON.parseObject(jsonStr, Map.class);
                String tableName = String.valueOf(map.get("name"));
                String jsonString = getJsonSchema(rule.getJsonSchemaFileName(), map);
                JsonSchema schema = schemaFactory.getSchema(jsonString, schemaValidatorsConfig);
                Set<ValidationMessage> validate = schema.validate(jsonStr, InputFormat.JSON);
                if (!CollectionUtil.isEmpty(validate)) {
                    Iterator<ValidationMessage> iterator = validate.iterator();
                    while (iterator.hasNext()) {
                        ValidationMessage next = iterator.next();
                        String message = next.getMessage();
                        log.error(String.format("%s schema validation fail: %s", tableName, message));
                        throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(),
                                CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                                String.format("%s schema validation fail: %s", tableName, message));
                    }
                }
            }
        }
        return true;
    }

    public boolean validateDSL(String jsonStr) {
        for (ValidationRule rule : rules) {
            if (rule.getType().equals("dsl")){
                try {
                    Resource resource = new ClassPathResource(rule.getJsonSchemaFileName());
                    String jsonSchemaStr = new String(IOUtils.toByteArray(resource.getStream()), StandardCharsets.UTF_8);
                    JsonSchema schema = schemaFactory.getSchema(jsonSchemaStr, schemaValidatorsConfig);
                    Set<ValidationMessage> validate = schema.validate(jsonStr, InputFormat.JSON);
                    if (!CollectionUtil.isEmpty(validate)) {
                        Iterator<ValidationMessage> iterator = validate.iterator();
                        while (iterator.hasNext()) {
                            ValidationMessage next = iterator.next();
                            String message = next.getMessage();
                            log.error(String.format("DSL validation fail: %s", message));
                            throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(),
                                    CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                                    String.format("DSL validation fail: %s", message));
                        }
                    }
                } catch (IOException e) {
                    throw new BusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(), e.getMessage());
                }
            }
        }
        return true;
    }

    private static String getJsonSchema(String jsonSchemaFileName, Map map) {
        try {
            Resource resource = new ClassPathResource(jsonSchemaFileName);
            String jsonSchemaStr = new String(IOUtils.toByteArray(resource.getStream()), StandardCharsets.UTF_8);
            StringBuffer sb = new StringBuffer();
            if (jsonSchemaStr.contains(ENUM_VALUES)) {
                List<Map<String, Object>> fields = (List<Map<String, Object>>) map.get("fields");
                for (Map<String, Object> field : fields) {
                    String name = String.valueOf(field.get("name"));
                    sb.append("\"").append(name).append("\"").append(",");
                }
                if (map.containsKey("doc")) {
                    Map docMap = (Map) map.get("doc");
                    if (docMap.containsKey(EXPRESSION_FIELDS)) {
                        List<Map<String, Object>> expressionFields = (List<Map<String, Object>>) docMap.get(EXPRESSION_FIELDS);
                        for (Map<String, Object> field : expressionFields) {
                            String name = String.valueOf(field.get("name"));
                            sb.append("\"").append(name).append("\"").append(",");
                        }
                    }
                }
                sb.setLength(sb.length() - 1);
                return jsonSchemaStr.replace(ENUM_VALUES, sb.toString());
            } else {
                return jsonSchemaStr;
            }
        } catch (IOException e) {
            throw new BusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(), e.getMessage());
        }
    }

    @Data
    public static class ValidationRule {
        private final String jsonSchemaFileName;
        private final String type;
    }
}
