package com.mesalab.qgw.controller;

import cn.hutool.core.util.NumberUtil;
import com.jayway.jsonpath.JsonPath;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.constant.MessageConst;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.qgw.exception.QGWErrorCode;
import com.mesalab.qgw.service.MetadataService;
import com.zdjizhi.utils.JsonMapper;
import com.zdjizhi.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
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
            throw new QGWBusinessException(ResultStatusEnum.FAIL.getCode(), QGWErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(QGWErrorCode.PARAMETER_ERROR.getMessage(),MessageConst.SCHEMA_PARAM_ERROR));
        }
        validationParam(name, obj);
        return metadataService.updateSchema(name, obj);
    }

    private void validationParam(String name, Map<String, Object> obj) {
        if (!metadataService.getAllTable().contains(name)) {
            throw new QGWBusinessException(ResultStatusEnum.FAIL.getCode(), QGWErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(QGWErrorCode.PARAMETER_ERROR.getMessage(),name + " not support setting TTL"));
        }
        String param = JsonMapper.toJsonString(obj);
        if (!VISIBILITY.containsAll(JsonPath.read(param, "$.fields[?(@.doc.visibility != null)].doc.visibility"))) {
            throw new QGWBusinessException(ResultStatusEnum.FAIL.getCode(), QGWErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(QGWErrorCode.PARAMETER_ERROR.getMessage(),MessageConst.SCHEMA_ERROR_VISIBILITY_VALUE));
        }
        List<Object> schemaDocTTL = JsonPath.read(param, "$.[?(@.doc.ttl != null)].doc.ttl");
        if (StringUtil.isNotEmpty(schemaDocTTL.stream().filter(o -> !(NumberUtil.isLong(String.valueOf(o)))).toArray())) {
            throw new QGWBusinessException(ResultStatusEnum.FAIL.getCode(), QGWErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(QGWErrorCode.PARAMETER_ERROR.getMessage(),MessageConst.SCHEMA_ERROR_TTL_VALUE));
        }
        List<Object> fieldDocTTL = JsonPath.read(param, "$.fields[?(@.doc.ttl != null)].doc.ttl");
        Object[] objects3 = fieldDocTTL.parallelStream().filter(o -> !NumberUtil.isLong(String.valueOf(o))).toArray();
        if (StringUtil.isNotEmpty(objects3)) {
            throw new QGWBusinessException(ResultStatusEnum.FAIL.getCode(), QGWErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(QGWErrorCode.PARAMETER_ERROR.getMessage(),MessageConst.SCHEMA_ERROR_TTL_VALUE));
        }
    }
}
