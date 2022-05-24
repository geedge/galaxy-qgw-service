package com.mesalab.qgw.controller;

import cn.hutool.core.util.NumberUtil;
import com.jayway.jsonpath.JsonPath;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.qgw.service.MetadataService;
import com.zdjizhi.utils.JsonMapper;
import com.zdjizhi.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping(value = "/metadata")
public class MetadataController {

    @Autowired
    private MetadataService metadataService;

    private final static List<String> VISIBILITY = Arrays.asList("enabled", "hidden", "disabled");


    @RequestMapping("/schema/v1/{type}/{name}")
    public BaseResult getSchema(@PathVariable String type, @PathVariable String name) {
        log.debug("SCHEMA信息获取,参数为{} {}", type, name);

        if (StringUtil.isBlank(type) || StringUtil.isBlank(name)) {

            return BaseResultGenerator.success4Message("ok");
        }
        return BaseResultGenerator.success(metadataService.getSchemaInfo(type, name));
    }

    @PutMapping("/schema/v1/fields/{name}")
    public BaseResult updateSchema(@PathVariable String name, @RequestBody Map<String, Object> obj) {
        log.info("update Schema api, params is: {}-{}", name, obj);
        if (StringUtil.isBlank(name) || StringUtil.isEmpty(obj)) {
            throw new BusinessException(HttpStatus.SC_BAD_REQUEST, ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(), "schema param error");
        }
        validationParam(name, obj);
        return metadataService.updateSchema(name, obj);
    }

    private void validationParam(String name, Map<String, Object> obj) {
        if (!metadataService.getAllTable().contains(name)) {
            throw new BusinessException(HttpStatus.SC_BAD_REQUEST, ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(), name + " not support setting TTL");
        }
        String param = JsonMapper.toJsonString(obj);
        if (!VISIBILITY.containsAll(JsonPath.read(param, "$.fields[?(@.doc.visibility != null)].doc.visibility"))) {
            throw new BusinessException(HttpStatus.SC_BAD_REQUEST, ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(), "visibility value error");
        }
        List<Object> schemaDocTTL = JsonPath.read(param, "$.[?(@.doc.ttl != null)].doc.ttl");
        if (StringUtil.isNotEmpty(schemaDocTTL.stream().filter(o -> !(NumberUtil.isLong(String.valueOf(o)))).toArray())) {
            throw new BusinessException(HttpStatus.SC_BAD_REQUEST, ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(), "ttl value error");
        }
        List<Object> fieldDocTTL = JsonPath.read(param, "$.fields[?(@.doc.ttl != null)].doc.ttl");
        Object[] objects3 = fieldDocTTL.parallelStream().filter(o -> !NumberUtil.isLong(String.valueOf(o))).toArray();
        if (StringUtil.isNotEmpty(objects3)) {
            throw new BusinessException(HttpStatus.SC_BAD_REQUEST, ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(), "ttl value error");
        }
    }
}
