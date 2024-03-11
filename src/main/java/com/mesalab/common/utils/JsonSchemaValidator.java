package com.mesalab.common.utils;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.io.resource.ClassPathResource;
import cn.hutool.core.io.resource.Resource;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.databind.JsonNode;
import com.mesalab.common.enums.HttpStatusCodeEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.networknt.schema.*;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;


public class JsonSchemaValidator {
    private static final Log log = LogFactory.get();
    private static final JsonSchemaValidator INSTANCE = new JsonSchemaValidator();
    private final List<String> rules;
    private static final String ENUM_VALUES = "\"$ENUM_VALUES$\"";
    private static final String EXPRESSION_FIELDS = "expression_fields";

    private JsonSchemaValidator() {
        this.rules = new ArrayList<>();
    }

    public static JsonSchemaValidator getInstance() {
        return INSTANCE;
    }

    public JsonSchemaValidator addRule(String jsonSchemaFileName) {
        rules.add(jsonSchemaFileName);
        return this;
    }


    public boolean validate(String jsonStr) {
        JsonSchemaFactory schemaFactory = JsonSchemaFactory
                .builder(com.networknt.schema.JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7))
                .build();
        SchemaValidatorsConfig schemaValidatorsConfig = new SchemaValidatorsConfig();
        schemaValidatorsConfig.setLocale(Locale.ENGLISH);
        for (String jsonSchemaFileName : rules) {
            Map map = JSON.parseObject(jsonStr, Map.class);
            String tableName = String.valueOf(map.get("name"));
            String jsonString = getJsonSchema(jsonSchemaFileName, map);
            JsonSchema schema = schemaFactory.getSchema(JsonMapper.fromJsonString(jsonString, JsonNode.class), schemaValidatorsConfig);
            Set<ValidationMessage> validate = schema.validate(JsonMapper.fromJsonString(jsonStr, JsonNode.class));
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
}
