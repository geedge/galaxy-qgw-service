package com.mesalab.qgw.controller;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.NumberUtil;
import com.alibaba.fastjson2.JSON;
import com.clearspring.analytics.util.Lists;
import com.jayway.jsonpath.JsonPath;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.MetadataType;
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
    @AuditLog("DatabaseController.getSchemaByTable")
    public BaseResult getSchemaByTable(@PathVariable("table_name") String tableName) {
        log.debug("SCHEMA信息获取,参数为{} {}", tableName);

        if (StringUtil.isBlank(tableName)) {
            return BaseResultGenerator.success4Message("ok");
        }
        return BaseResultGenerator.success(databaseService.getSchemaInfo(MetadataType.FIELDS.getValue(), tableName, true));
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

    @RequestMapping(value = "/{dbName}/table", consumes = "application/x-www-form-urlencoded")
    @AuditLog("DatabaseController.getTableByDB")
    public BaseResult getTableByDB(@PathVariable String dbName) {
        log.debug("SCHEMA信息获取,参数为{} {}", dbName);

        if (StringUtil.isBlank(dbName)) {

            return BaseResultGenerator.success4Message("ok");
        }
        return BaseResultGenerator.success(databaseService.getSchemaInfo(MetadataType.TABLES.getValue(), dbName, true));
    }

    @GetMapping(value = "/storage/quota", consumes = "application/x-www-form-urlencoded")
    @AuditLog("DatabaseController.storageQuota")
    public BaseResult storageQuota() {
        return databaseService.getStorageQuota();
    }

    @PutMapping(value = "/storage/quota", consumes = "application/json")
    @AuditLog("DatabaseController.storageSetting")
    public BaseResult storageSetting(@RequestBody List<StorageDeletionInfo> list) {
        log.warn("数据配额设置, 参数: params is {}", list);
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
        return databaseService.deleteStorage(list);
    }

    @RequestMapping(value = "/storage/quota", method = RequestMethod.DELETE, consumes = "application/x-www-form-urlencoded")
    @AuditLog("DatabaseController.storageDeletion")
    public BaseResult storageDeletion(@RequestParam String type) {
        List<StorageDeletionInfo> deletionInfoList = Lists.newArrayList();
        StorageDeletionInfo deletionInfo = new StorageDeletionInfo();
        deletionInfo.setType(type);
        deletionInfo.setMaxDays(0);
        deletionInfoList.add(deletionInfo);
        return databaseService.deleteStorage(deletionInfoList);
    }

    @RequestMapping(value = "/storage/quota/daily_usage", consumes = "application/x-www-form-urlencoded")
    @AuditLog("DatabaseController.dailyTrendOfStorage")
    public BaseResult dailyTrendOfStorage(@RequestParam Map<String, Object> param) {
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
