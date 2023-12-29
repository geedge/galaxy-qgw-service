package com.mesalab.qgw.controller;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.NumberUtil;
import com.alibaba.fastjson2.JSON;
import com.jayway.jsonpath.JsonPath;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.MetadataTypeEnum;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.qgw.service.DatabaseService;
import com.geedgenetworks.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping(value = "v1/database")
public class DatabaseController {

    @Autowired
    private DatabaseService databaseService;

    private final static List<String> VISIBILITY = Arrays.asList("enabled", "hidden", "disabled");


    @RequestMapping(value = "/table/{table_name}/schema", consumes = "application/x-www-form-urlencoded")
    public BaseResult getSchemaByTable(@PathVariable("table_name") String tableName) {
        log.debug("SCHEMA信息获取,参数为{} {}", tableName);

        if (StringUtil.isBlank(tableName)) {
            return BaseResultGenerator.success4Message("ok");
        }
        return BaseResultGenerator.success(databaseService.getSchemaInfo(MetadataTypeEnum.FIELDS.getValue(), tableName, true));
    }

    @PutMapping(value = "/table/{table_name}/schema", consumes = "application/json")
    public BaseResult updateSchema(@PathVariable("table_name") String tableName, @RequestBody Map<String, Object> obj) {
        log.info("update Schema api, params is: {}-{}", tableName, obj);
        if (StringUtil.isBlank(tableName) || StringUtil.isEmpty(obj)) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), QGWMessageConst.SCHEMA_PARAM_ERROR));
        }
        validationParam(tableName, obj);
        return databaseService.updateSchema(tableName, obj);
    }

    @RequestMapping(value = "/{dbName}/table", consumes = "application/x-www-form-urlencoded")
    public BaseResult getTableByDB(@PathVariable String dbName) {
        log.debug("SCHEMA信息获取,参数为{} {}", dbName);

        if (StringUtil.isBlank(dbName)) {

            return BaseResultGenerator.success4Message("ok");
        }
        return BaseResultGenerator.success(databaseService.getSchemaInfo(MetadataTypeEnum.TABLES.getValue(), dbName, true));
    }

    private void validationParam(String name, Map<String, Object> obj) {
        if (!databaseService.getAllTable().contains(name)) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), name + QGWMessageConst.NOT_SUPPORT_SETTING_TTL));
        }
        String param = JSON.toJSONString(obj);
        if (!VISIBILITY.containsAll(JsonPath.read(param, "$.fields[?(@.doc.visibility != null)].doc.visibility"))) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), QGWMessageConst.SCHEMA_ERROR_VISIBILITY_VALUE));
        }
        List<Object> schemaDocTTL = JsonPath.read(param, "$.[?(@.doc.ttl != null)].doc.ttl");
        if (StringUtil.isNotEmpty(schemaDocTTL.stream().filter(o -> !(NumberUtil.isLong(String.valueOf(o)))).toArray())) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), QGWMessageConst.SCHEMA_ERROR_TTL_VALUE));
        }
        List<Object> fieldDocTTL = JsonPath.read(param, "$.fields[?(@.doc.ttl != null)].doc.ttl");
        Object[] objects3 = fieldDocTTL.parallelStream().filter(o -> !NumberUtil.isLong(String.valueOf(o))).toArray();
        if (StringUtil.isNotEmpty(objects3)) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), QGWMessageConst.SCHEMA_ERROR_TTL_VALUE));
        }
        if (schemaDocTTL.size() > 0 && fieldDocTTL.size() > 0
                && CollectionUtil.max(schemaDocTTL.stream().map(o -> Long.parseLong(o.toString())).collect(Collectors.toList())) < CollectionUtil.max(fieldDocTTL.stream().map(o -> Long.parseLong(o.toString())).collect(Collectors.toList()))) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), QGWMessageConst.SCHEMA_ERROR_TTL_SIZE));
        }
    }
}
