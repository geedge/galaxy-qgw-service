package com.mesalab.knowledge;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ProcessingMessage;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.mesalab.GalaxyQGWApplicationTests;
import org.apache.commons.lang3.Validate;
import org.junit.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

import java.io.IOException;
import java.util.Iterator;

/**
 * @description:
 * @author: zhq
 * @create: 2020-07-31
 **/
@EnableAutoConfiguration
public class JsonSchemaTest extends GalaxyQGWApplicationTests {
    private static final Log log = LogFactory.get();

    @Test
    public void jsonTest() {
        //创建jsonschema工厂
        String jsonStr = "{\n" +
                "    \"clientId\": null,\n" +
                "    \"query\": {\n" +
                "        \"queryType\": \"iplearning\",\n" +
                "        \"dataSource\": \"IP_LEARNING_VIEW\",\n" +
                "        \"parameters\": {\n" +
                "            \"match\": [\n" +
                "                {\n" +
                "                    \"type\": \"substring\",\n" +
                "                    \"fieldKey\": \"FQDN_NAME\",\n" +
                "                    \"fieldValues\": \n" +
                "                        [\"360\"]\n" +
                "                    \n" +
                "                }\n" +
                "            ],\n" +
                "            \"range\": [\n" +
                "                {\n" +
                "                    \"type\": \"ge\",\n" +
                "                    \"fieldKey\": \"PROTOCOL\",\n" +
                "                    \"fieldValues\": [\n" +
                "                       \"HTTP\"\n" +
                "                    ]\n" +
                "                },\n" +
                "                {\n" +
                "                    \"type\": \"eq\",\n" +
                "                    \"fieldKey\": \"DEPTH\",\n" +
                "                    \"fieldValues\": [\n" +
                "                        1\n" +
                "                    ]\n" +
                "                },\n" +
                "                {\n" +
                "                    \"type\": \"ge\",\n" +
                "                    \"fieldKey\": \"UNIQ_CIP\",\n" +
                "                    \"fieldValues\": \n" +
                "                        [5]\n" +
                "                    \n" +
                "                }\n" +
                "            ],\n" +
                "            \"intervals\": [\n" +
                "                \"2020-07-01 00:00:00/2020-08-02 00:00:00\"]\n" +
                "            ,\n" +
                "            \"limit\": \"15\"\n" +
                "        }\n" +
                "    }\n" +
                "}";
        try {
            JsonSchemaFactory factory = JsonSchemaFactory.byDefault();
            //通过jsonschemaFactory获取jsonnode对象
            JsonNode schemaNode = JsonLoader.fromResource("/dsl-validation.json");
            //通过jsonstr字符串获取对应的jsonnode对象
            JsonNode dataNode = JsonLoader.fromString(jsonStr);
            JsonSchema jsonSchema = factory.getJsonSchema(schemaNode);
            //使用json-schema-validator中的jsonschema对象的validate方法对数据进行校验
            //获取处理的报告信息
            ProcessingReport processingReport = jsonSchema.validate(dataNode);
            //获取完整的报告信息
            Iterator<ProcessingMessage> iterator = processingReport.iterator();
            StringBuffer sb = new StringBuffer();
            while (iterator.hasNext()) {
                ProcessingMessage next = iterator.next();
                JsonNode jsonNode = next.asJson();
                sb.append("pointer on ");
                sb.append(jsonNode.get("instance").get("pointer"));
                sb.append(", ");
                sb.append(next.getMessage());
                sb.append(". ");
            }
            //判断校验是否成功，如果为true成功
            Validate.isTrue(processingReport.isSuccess(), sb.toString());
        } catch (ProcessingException e) {
            log.error("ProcessingException information: ", e.getMessage());
        } catch (IOException e) {
            log.error("IOException information: ", e.getMessage());
        } catch (Exception e) {
            log.error("Other Exception information: ", e.getMessage());
        }
    }
}
