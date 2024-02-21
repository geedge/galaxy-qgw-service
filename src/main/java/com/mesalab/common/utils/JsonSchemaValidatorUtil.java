package com.mesalab.common.utils;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.io.resource.ClassPathResource;
import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.mesalab.common.enums.HttpStatusCodeEnum;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.networknt.schema.*;
import lombok.Data;
import org.nutz.lang.Files;

import java.nio.file.Paths;
import java.util.*;


public class JsonSchemaValidatorUtil {
    private static final Log log = LogFactory.get();
    private static String schemaId = "https://schemas.amazon.com/selling-partners/definitions/product-types/meta-schema/v1";
    private final List<ValidationRule> rules;
    private static final String ENUM_VALUES = "\"$ENUM_VALUES$\"";
    private static final String EXPRESSION_FIELDS = "expression_fields";
    private static final String ERROR = "ERROR";
    private static final String WARN = "WARN";

    public JsonSchemaValidatorUtil() {
        this.rules = new ArrayList<>();
    }

    public JsonSchemaValidatorUtil addRule(String jsonSchemaFileName, String message, String ruleAlarmLevel) {
        rules.add(new ValidationRule(jsonSchemaFileName, message, ruleAlarmLevel));
        return this;
    }


    public List<String> validate(String jsonStr) {
        List<String> data = Lists.newArrayList();

        JsonMetaSchema standardMetaSchema = JsonMetaSchema.getV201909();
        JsonMetaSchema metaSchema = JsonMetaSchema.builder(schemaId, standardMetaSchema)
                .build();

        JsonSchemaFactory schemaFactory = JsonSchemaFactory
                .builder(com.networknt.schema.JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7))
                .objectMapper(new ObjectMapper())
                .addMetaSchema(metaSchema)
                .build();

        for (ValidationRule rule : rules) {
            Map map = JSON.parseObject(jsonStr, Map.class);
            String tableName = String.valueOf(map.get("name"));
            String jsonString = getJsonSchema(rule, map);
            JsonSchema schema = schemaFactory.getSchema(JsonMapper.fromJsonString(jsonString, JsonNode.class));
            Set<ValidationMessage> validate = schema.validate(JsonMapper.fromJsonString(jsonStr, JsonNode.class));
            if (!CollectionUtil.isEmpty(validate)) {
                List<String> messageList = Lists.newArrayList();
                Iterator<ValidationMessage> iterator = validate.iterator();
                while (iterator.hasNext()) {
                    ValidationMessage next = iterator.next();
                    String message = String.format(rule.getMessage(), StrUtil.subBefore(next.getMessage(), "：", false));
                    if (rule.getRuleAlarmLevel().equals(ERROR)) {
                        log.error(String.format("%s schema validation fail: %s", tableName, message));
                        throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(),
                                CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                                String.format("%s schema validation fail: %s", tableName, message));
                    } else if (rule.getRuleAlarmLevel().equals(WARN)) {
                        messageList.add(message);
                    }
                }
                for (String s : messageList) {
                    data.add(s);
                }
            }
        }
        return data;
    }

    private static String getJsonSchema(ValidationRule rule, Map map) {
        String jsonSchemaStr = new String(Files.readBytes(Paths.get(new ClassPathResource(rule.getJsonSchemaFileName()).getAbsolutePath()).toFile()));
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
    }

    @Data
    public static class ValidationRule {
        private final String jsonSchemaFileName;
        private final String message;
        private final String ruleAlarmLevel;

        ValidationRule(String jsonSchemaFileName, String message, String ruleAlarmLevel) {
            this.jsonSchemaFileName = jsonSchemaFileName;
            this.message = message;
            this.ruleAlarmLevel = ruleAlarmLevel;
        }

    }
}
