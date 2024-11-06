package com.mesalab.qgw.controller;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.google.common.collect.Sets;
import com.jayway.jsonpath.JsonPath;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.MetadataType;
import com.mesalab.common.enums.StorageQuotaLogType;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.common.enums.HttpStatusCodeEnum;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.qgw.model.basic.AuditLog;
import com.mesalab.qgw.model.job.StorageDeletionInfo;
import com.mesalab.qgw.service.DatabaseService;
import com.geedgenetworks.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping(value = "v1/database")
public class DatabaseController {

    @Autowired
    private DatabaseService databaseService;

    private final static HashSet<String> VISIBILITY = Sets.newHashSet("enabled", "hidden", "disabled");
    private final static HashSet<Object> IS_ENCRYPTED = Sets.newHashSet(true, false, "true", "false");


    @RequestMapping(value = "/table/{table_name}/schema", method = RequestMethod.GET, consumes = "application/x-www-form-urlencoded")
    @AuditLog("DatabaseController.getSchemaByTable")
    public BaseResult getSchemaByTable(@PathVariable("table_name") String tableName
            , @RequestParam(value = "option", required = false) String option) {
        log.debug("get Schema api, params is: {}", tableName);
        if (StringUtil.isBlank(tableName)) {
            return BaseResultGenerator.success4Message("ok");
        }
        if (StrUtil.isBlankIfStr(option)) {
            return BaseResultGenerator.success(databaseService.getSchemaInfo(MetadataType.FIELDS.getValue(), tableName, true));
        }
        List<String> tableEncryptedFields = databaseService.getTableEncryptedFields(tableName);
        return BaseResultGenerator.success(tableEncryptedFields);
    }

    @PutMapping(value = "/table/{table_name}/schema", consumes = "application/json")
    @AuditLog("DatabaseController.updateSchema")
    public BaseResult updateSchema(@PathVariable("table_name") String tableName, @RequestBody Map<String, Object> obj) {
        log.info("update Schema api, params is: {}-{}", tableName, obj);
        if (StringUtil.isBlank(tableName) || StringUtil.isEmpty(obj)) {
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.SCHEMA_PARAM_ERROR));
        }
        validationParam(tableName, obj);
        return databaseService.updateSchema(tableName, obj);
    }

    @RequestMapping(value = "/{dbName}/table", method = RequestMethod.GET, consumes = "application/x-www-form-urlencoded")
    @AuditLog("DatabaseController.getTableByDB")
    public BaseResult getTableByDB(@PathVariable String dbName) {
        log.debug("get tables api, params is: {}", dbName);
        if (StringUtil.isBlank(dbName)) {

            return BaseResultGenerator.success4Message("ok");
        }
        return BaseResultGenerator.success(databaseService.getSchemaInfo(MetadataType.TABLES.getValue(), dbName, true));
    }

    @GetMapping(value = "/storage/quota", consumes = "application/x-www-form-urlencoded")
    @AuditLog("DatabaseController.storageQuota")
    public BaseResult getStorageQuotaCfg() {
        return databaseService.getStorageQuotaCfg();
    }

    @PutMapping(value = "/storage/quota", consumes = "application/json")
    @AuditLog("DatabaseController.storageSetting")
    public BaseResult putStorageQuotaCfg(@RequestBody List<StorageDeletionInfo> list) {
        log.warn("setting storage quota, params is :{}", list);
        if (StringUtil.isEmpty(list)) {
            return BaseResultGenerator.failure(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.PARAMETER_NOT_OBTAINED));
        }
        for (StorageDeletionInfo info : list) {
            if (StringUtil.isBlank(info.getType()) || StringUtil.isBlank(String.valueOf(info.getMaxDays()))) {
                throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                        String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.LOGTYPE_AND_MAXDAYS_NOT_NULL));
            }
            if ("ALL".equalsIgnoreCase(info.getType()) && list.size() != 1) {
                throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                        String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.CANNOT_COEXIST_OTHER_TYPES));
            }
            if (!(info.getMaxDays() >= 0 && info.getMaxDays() <= 2000)) {
                throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                        String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.MAXDAYS_ERROR));
            }
        }
        return databaseService.updateStorageQuotaCfg(list);
    }

    @RequestMapping(value = "/storage/quota", method = RequestMethod.DELETE, consumes = "application/x-www-form-urlencoded")
    @AuditLog("DatabaseController.storageDeletion")
    public BaseResult cleanData(@RequestParam String type) {
        log.warn("clean storage quota, params is :{}", type);
        if (Arrays.stream(StorageQuotaLogType.values()).noneMatch(value -> value.getValue().equals(type))) {
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), "not exist type: " + type));
        }
        return databaseService.cleanDataByType(type);
    }

    @RequestMapping(value = "/storage/quota/daily-usage", method = RequestMethod.GET, consumes = "application/x-www-form-urlencoded")
    @AuditLog("DatabaseController.dailyTrendOfStorage")
    public BaseResult dailyTrendOfStorage(@RequestParam Map<String, Object> param) {
        log.debug("get storage quota trend api, params is: {}", param);
        String searchStartTime = StringUtil.stripToEmpty((String) param.get("start_time"));
        String searchEndTime = StringUtil.stripToEmpty((String) param.get("end_time"));
        return databaseService.dailyTrendOfStorage(searchStartTime, searchEndTime);
    }

    private void validationParam(String name, Map<String, Object> obj) {
        if (!databaseService.getAllTable().contains(name)) {
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), name + QGWMessageConst.NOT_SUPPORT_SETTING_TTL));
        }
        String param = JSON.toJSONString(obj);
        if (!VISIBILITY.containsAll(JsonPath.read(param, "$.fields[?(@.doc.visibility != null)].doc.visibility"))) {
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.SCHEMA_ERROR_VISIBILITY_VALUE));
        }
        if (!IS_ENCRYPTED.containsAll(JsonPath.read(param, "$.fields[?(@.doc.is_encrypted != null)].doc.is_encrypted"))) {
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.SCHEMA_ERROR_VISIBILITY_VALUE));
        }
        List<Object> schemaDocTTL = JsonPath.read(param, "$.[?(@.doc.ttl != null)].doc.ttl");
        if (StringUtil.isNotEmpty(schemaDocTTL.stream().filter(o -> !(NumberUtil.isLong(String.valueOf(o)))).toArray())) {
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.SCHEMA_ERROR_TTL_VALUE));
        }
        List<Object> fieldDocTTL = JsonPath.read(param, "$.fields[?(@.doc.ttl != null)].doc.ttl");
        Object[] objects3 = fieldDocTTL.parallelStream().filter(o -> !NumberUtil.isLong(String.valueOf(o))).toArray();
        if (StringUtil.isNotEmpty(objects3)) {
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.SCHEMA_ERROR_TTL_VALUE));
        }
        if (schemaDocTTL.size() > 0 && fieldDocTTL.size() > 0
                && CollectionUtil.max(schemaDocTTL.stream().map(o -> Long.parseLong(o.toString())).collect(Collectors.toList())) < CollectionUtil.max(fieldDocTTL.stream().map(o -> Long.parseLong(o.toString())).collect(Collectors.toList()))) {
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.SCHEMA_ERROR_TTL_SIZE));
        }
    }
}
