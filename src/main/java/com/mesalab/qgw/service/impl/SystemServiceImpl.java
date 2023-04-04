package com.mesalab.qgw.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.base.CaseFormat;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayway.jsonpath.JsonPath;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.*;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.common.utils.sqlparser.SQLHelper;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.qgw.exception.QGWErrorCode;
import com.mesalab.qgw.model.api.ClickHouseHttpSource;
import com.mesalab.qgw.model.api.HttpConfig;
import com.mesalab.qgw.dialect.Dialect;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.JobAdminHttpSource;
import com.mesalab.qgw.model.job.EncryptionInfo;
import com.mesalab.qgw.model.job.ExecutorParam;
import com.mesalab.qgw.model.job.StorageDeletionInfo;
import com.mesalab.qgw.model.job.XxlJobInfo;
import com.mesalab.qgw.service.ApiService;
import com.mesalab.qgw.service.MetadataService;
import com.mesalab.qgw.service.SystemService;
import com.mesalab.services.common.property.SqlPropertySourceFactory;
import com.zdjizhi.utils.DateUtils;
import com.zdjizhi.utils.Encodes;
import com.zdjizhi.utils.StringUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.jasypt.util.text.BasicTextEncryptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cglib.beans.BeanMap;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service("systemService")
@PropertySource(value = "classpath:http-sql-template.sql", factory = SqlPropertySourceFactory.class)
public class SystemServiceImpl implements SystemService, EnvironmentAware {
    private static final Log log = LogFactory.get();
    private static Pattern pTTL = Pattern.compile(".*toIntervalSecond\\((\\d+)\\)", Pattern.CASE_INSENSITIVE);
    private static final String trafficPort = "8123";
    private Environment env;
    @Autowired
    private ApiService apiService;
    @Autowired
    private JobAdminHttpSource jobAdminHttpSource;
    @Autowired
    private HttpClientService httpClientService;
    @Resource(name = "metadataService")
    private MetadataService metadataService;
    @Autowired
    HttpConfig httpConfig;
    @Autowired
    ClickHouseHttpSource clickHouseHttpSource;

    public final static String queryIdSeparator = ":";

    private Map<String, String> headers = Maps.newHashMap();

    @Override
    public BaseResult getStorageQuota() {

        //封装sql进行查询：Analytic Logs、Files、Traffic Logs
        ApiParam param = new ApiParam();
        param.setQuery(env.getProperty("SYSTEM_STORAGE_QUOTA"));
        BaseResult result = apiService.executeQuery(param);
        return resetBaseResult(result, "log_type");
    }

    @Override
    public BaseResult dailyTrendOfStorage(String searchStartTime, String searchEndTime) {

        Date currentDate = DateUtils.convertStringToDate(DateUtils.getCurrentDate(), DateUtils.YYYY_MM_DD);

        if (StringUtil.isBlank(searchStartTime) && StringUtil.isBlank(searchEndTime)) {
            searchStartTime = DateUtils.getFormatDate(DateUtils.getSomeDate(currentDate, -7), DateUtils.YYYY_MM_DD_HH24_MM_SS);
            searchEndTime = DateUtils.getFormatDate(currentDate, DateUtils.YYYY_MM_DD_HH24_MM_SS);
        }

        if (StringUtil.isNotBlank(searchStartTime) && StringUtil.isNotBlank(searchEndTime)) {
            ApiParam param = new ApiParam();
            String sql = String.format(env.getProperty("SYSTEM_DAILY_TREND_OF_STORAGE"), searchStartTime, searchEndTime);
            param.setQuery(sql);
            BaseResult result = apiService.executeQuery(param);
            return resetBaseResult(result, "type");
        } else {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), QGWMessageConst.START_TIME_AND_END_TIME_NOT_NULL));
        }
    }

    private BaseResult resetBaseResult(BaseResult result, String alias) {
        if (!result.isSuccess()) {
            return result;
        }
        List<Map> data = (List<Map>) result.getData();
        data.forEach(o -> o.put(alias, LogType.getLabelByValue(String.valueOf(o.get(alias)))));
        return result;
    }

    @Override
    public BaseResult deleteStorage(List<StorageDeletionInfo> list) {

        if (!logTypeValid(list)) {
            return BaseResultGenerator.failure(ResultStatusEnum.SERVER_ERROR.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),"Match failed, please check log type!"));
        }

        preProcessOfLogType(list);

        if (jobIsBusy(list)) {
            return BaseResultGenerator.failure(ResultStatusEnum.LOCKED.getCode(),  ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),"The task is busy, please try later!"));
        }

        BaseResult baseResult = null;
        for (StorageDeletionInfo info : list) {
            baseResult = executeDeleteStorageJob(info);
        }
        return baseResult;
    }

    @Override
    public BaseResult getDeleteStorageStatus(String logType) {
        setCookie();
        List<StorageDeletionInfo> list = new ArrayList<>();
        if (StringUtil.isBlank(logType)) {

            StorageDeletionInfo trafficInfo = getStorageDeletionInfoByHandler(LogType.TRAFFIC_LOGS.getValue(), JobHandlerEnum.DELETE_TRAFFIC_DATA_JOB_HANDLER.getValue());
            list.add(trafficInfo);

            StorageDeletionInfo reportInfo = getStorageDeletionInfoByHandler(LogType.REPORT_AND_METRICS.getValue(), JobHandlerEnum.DELETE_REPORT_AND_METRICS_DATA_JOB_HANDLER.getValue());
            list.add(reportInfo);

            StorageDeletionInfo fileInfo = getStorageDeletionInfoByHandler(LogType.FILES.getValue(), JobHandlerEnum.DELETE_FILES_JOB_HANDLER.getValue());
            list.add(fileInfo);
        } else if (LogType.TRAFFIC_LOGS.getValue().equalsIgnoreCase(logType)) {
            StorageDeletionInfo trafficInfo = getStorageDeletionInfoByHandler(logType, JobHandlerEnum.DELETE_TRAFFIC_DATA_JOB_HANDLER.getValue());
            list.add(trafficInfo);
        } else if (LogType.REPORT_AND_METRICS.getValue().equalsIgnoreCase(logType)) {
            StorageDeletionInfo reportInfo = getStorageDeletionInfoByHandler(logType, JobHandlerEnum.DELETE_REPORT_AND_METRICS_DATA_JOB_HANDLER.getValue());
            list.add(reportInfo);
        } else if (LogType.FILES.getValue().equalsIgnoreCase(logType)) {
            StorageDeletionInfo reportInfo = getStorageDeletionInfoByHandler(logType, JobHandlerEnum.DELETE_FILES_JOB_HANDLER.getValue());
            list.add(reportInfo);
        } else {
            return BaseResultGenerator.failure(ResultStatusEnum.SERVER_ERROR.getCode(), "No matching log type: " + logType);
        }
        list.forEach(o -> o.setLogType(LogType.getLabelByValue(o.getLogType())));
        return BaseResultGenerator.success("ok", list);
    }

    @Override
    public BaseResult deleteQueryTask(String queryId) {
        Dialect dialect = buildAndGetDialect(queryId);
        return dialect.executeKillQuery(queryId);
    }

    @Override
    public String getCustomQueryId(String resultId, String query) {
        String tableName = SQLHelper.getTableName(query);
        String dbType = metadataService.getDBTypeByTableName(tableName);
        return DigestUtil.md5Hex(dbType) + queryIdSeparator + DigestUtil.md5Hex(resultId + query.trim());
    }



    @Override
    public BaseResult getProcessesByQueryId(String queryId) {
        Dialect dialect = buildAndGetDialect(queryId);
        return dialect.getProcesses(queryId);
    }

    @Override
    public BaseResult getCiphertext(EncryptionInfo param) {
        BaseResult baseResult;
        BasicTextEncryptor textEncryptor = new BasicTextEncryptor();
        textEncryptor.setPassword(param.getSalt());
        String encrypt = textEncryptor.encrypt(param.getPassword());
        Map dataMap = new HashMap();
        dataMap.put("password", param.getPassword());
        dataMap.put("encrypted_password", encrypt);
        baseResult = BaseResultGenerator.success("ok", Lists.newArrayList(dataMap));
        return baseResult;
    }

    @Override
    public BaseResult consistencyCheck() {
        List<Map<String, Object>> dataList = Lists.newArrayList();
        Map<String, Object> logDiff = Maps.newHashMap();
        Map tables = metadataService.getSchemaInfo(MetadataTypeEnum.TABLES.getValue(), clickHouseHttpSource.getDbName());
        List<String> symbols = (List<String>) tables.get("symbols");
        logDiff.put("logType", LogType.TRAFFIC_LOGS.getValue());
        Map<String, Map<String, Object>> changeTTL = Maps.newHashMap();
        List<String> ipPorts = getClusterAddressOfCK();
        try {
            for (String ipPort : ipPorts) {
                if (!changeTTL.isEmpty()) {
                    break;
                }
                if (ipPort.startsWith("127.0.0.1")) {
                    String url = clickHouseHttpSource.getUrl();
                    ipPort = url.substring(url.indexOf("//") + 2, url.lastIndexOf(":") + 1).concat(trafficPort);
                }
                for (String tableName : symbols) {
                    Map schemaMap = metadataService.getSchemaInfo("fields", tableName);
                    Object tableTTLInSchema = getTableTTLInSchema(schemaMap);
                    Map<String, Object> tableDiff = getTableDiff(ipPort, tableName, tableTTLInSchema);
                    List fieldDiff = getFieldDiff(ipPort, tableName, schemaMap);
                    if (CollectionUtil.isNotEmpty(fieldDiff)) {
                        tableDiff.put("fields", fieldDiff);

                    }
                    if (CollectionUtil.isNotEmpty(tableDiff)) {
                        changeTTL.put(tableName, tableDiff);
                        logDiff.put("tables", Lists.newArrayList(changeTTL));
                        logDiff.put("address", Lists.newArrayList(ipPort));
                    }
                }
            }
        } catch (RuntimeException ex) {
            log.error(" schema TTL check error: {}", ex);
            throw new QGWBusinessException(ResultStatusEnum.SERVER_ERROR.getCode(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(),
                    String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(),ex.getMessage()));
        }
        if (changeTTL.isEmpty()) {
            return BaseResultGenerator.success("ok", dataList);
        }
        dataList.add(logDiff);
        return BaseResultGenerator.success("Log streaming setting task not executed or failed", dataList);
    }

    private Object getTableTTLInSchema(Map schemaMap) {
        Object tableTTLInSchema = null;
        if (StringUtil.isNotEmpty(schemaMap)) {
            if (schemaMap.containsKey("doc")) {
                Map<String, Object> schemaHasDoc = (Map<String, Object>) schemaMap.get("doc");
                if (schemaHasDoc.containsKey("ttl")) {
                    tableTTLInSchema = StringUtil.isEmpty(schemaHasDoc.get("ttl")) ? null : schemaHasDoc.get("ttl").toString();
                }
            }
        }
        return tableTTLInSchema;
    }

    private Map<String, Object> getTableDiff(String ipPort, String tableName, Object tableTTLInSchema) {
        String sql = String.format(Objects.requireNonNull(env.getProperty("TABLE_TTL")), tableName, clickHouseHttpSource.getDbName());
        Map<String, String> result = executeHttpGetOfCK(ipPort, sql, clickHouseHttpSource.getSystemDBName());
        if (Integer.parseInt(result.get("status")) != ResultStatusEnum.SUCCESS.getCode()) {
            log.error(" query table TTL error: {}", result.toString());
            throw new QGWBusinessException(ResultStatusEnum.SERVER_ERROR.getCode(), QGWErrorCode.SQL_EXECUTION_SERVER_EXCEPTION.getCode(),
                    String.format(QGWErrorCode.SQL_EXECUTION_SERVER_EXCEPTION.getMessage(),result.toString()));
        }
        Object tableTTLInDB = null;
        Map<String, Object> tableDiff = Maps.newHashMap();
        Map o = (Map)JsonMapper.fromJsonString(result.get("result"), Map.class);
        Map<String, Object> resultMap = (Map<String, Object>) o;
        for (Map<String, Object> datum : (List<Map<String, Object>>)resultMap.get("data")) {
            Matcher matcher = pTTL.matcher(String.valueOf(datum.get("table_ttl")));
            tableTTLInDB = matcher.find() ? matcher.group(1) : null;
        }
        if (!String.valueOf(tableTTLInDB).equals(String.valueOf(tableTTLInSchema))) {
            tableDiff.put("last_ttl", StringUtil.isEmpty(tableTTLInSchema) ? null : tableTTLInSchema.toString());
            tableDiff.put("used_ttl", StringUtil.isEmpty(tableTTLInDB) ? null : tableTTLInDB.toString());
            log.warn("table ttl: {} not consistency.", tableName);
        }
        return tableDiff;
    }

    private List<Map<String, Object>> getFieldDiff(String ipPort, String tableName, Map<String, Object> schemaMap) {
        List<Map<String, Object>> fieldDiff = Lists.newArrayList();
        Map<String, String> result = executeHttpGetOfCK(ipPort, "describe " + tableName + "_local", clickHouseHttpSource.getDbName());
        if (Integer.parseInt(result.get("status")) != ResultStatusEnum.SUCCESS.getCode()) {
            log.error(" query table field TTL error: {}", result.toString());
            throw new QGWBusinessException(ResultStatusEnum.SERVER_ERROR.getCode(), QGWErrorCode.SQL_EXECUTION_SERVER_EXCEPTION.getCode(),
                    String.format(QGWErrorCode.SQL_EXECUTION_SERVER_EXCEPTION.getMessage(),result.toString()));
        }
        List<Map<String, Object>> fields = (List<Map<String, Object>>) schemaMap.get("fields");
        for (Map<String, Object> field : fields) {
            Object read = JsonPath.read(result, "$.result");
            List<Object> fieldList = JsonPath.read(JSONUtil.toJsonStr(JSONUtil.parseObj(read)), "$.data[?(@.name == \"" + field.get("name") + "\")].ttl_expression");
            Object fieldTTLInSchema = null;
            Object fieldTTLInDB;
            if (StringUtil.isNotEmpty(field.get("doc"))) {
                Map<String, Object> doc = (Map<String, Object>) field.get("doc");
                fieldTTLInSchema = StringUtil.isEmpty(doc.get("ttl")) ? null : doc.get("ttl");
            }
            Matcher matcher = pTTL.matcher(String.valueOf(fieldList.get(0)));
            fieldTTLInDB = matcher.find() ? matcher.group(1) : null;
            if (String.valueOf(fieldTTLInSchema).equals(String.valueOf(fieldTTLInDB))) {
                continue;
            }
            log.warn("field ttl: address {} {}-{} not consistency.", ipPort, tableName, field.get("name"));
            Map<String, Object> ttlDiff = Maps.newHashMap();
            ttlDiff.put("last_ttl", StringUtil.isEmpty(fieldTTLInSchema) ? null : fieldTTLInSchema.toString());
            ttlDiff.put("used_ttl", StringUtil.isEmpty(fieldTTLInDB) ? null : fieldTTLInDB.toString());
            if (fieldDiff.stream().noneMatch(o -> o.containsKey(field.get("name").toString()))) {
                Map<String, Object> item = Maps.newHashMap();
                item.put(field.get("name").toString(), ttlDiff);
                fieldDiff.add(item);
            }
        }
        return fieldDiff;
    }

    private List<String> getClusterAddressOfCK() {
        List<String> endpointList = new ArrayList<>();
        BaseResult baseResult = apiService.executeQuery(ApiParam.builder().query("SELECT DISTINCT concat(host_address,':','" + trafficPort + "') as endpoint FROM clusters where cluster = 'ck_cluster'").build());
        if (baseResult.isSuccess()) {
            List<Map<String, Object>> data = (List<Map<String, Object>>) baseResult.getData();
            for (Map<String, Object> datum : data) {
                endpointList.add(String.valueOf(datum.get("endpoint")));
            }
        }
        return endpointList;
    }

    private Map<String, String> executeHttpGetOfCK(String ipPort, String sql, String dbName) {
        String queryURL = URLUtil.normalize("http://" + ipPort + "/?");
        StringBuilder paramBuilder = new StringBuilder("user=")
                .append(clickHouseHttpSource.getRealTimeAccountUserName()).append("&")
                .append("password=").append(clickHouseHttpSource.getRealTimeAccountPin()).append("&")
                .append("database=").append(dbName).append("&")
                .append("query=").append(sql)
                .append(" FORMAT JSON;");
        List<NameValuePair> values = URLEncodedUtils.parse(paramBuilder.toString(), StandardCharsets.UTF_8);
        int socketTimeOut = httpConfig.getCkRealTimeAccountSocketTimeOut();
        return httpClientService.httpGet(queryURL + URLEncodedUtils.format(values, "utf-8"), socketTimeOut);
    }

    public Dialect buildAndGetDialect(String queryId) {
        ApiParam apiParam = new ApiParam();
        apiParam.setDbType(parseDBType(queryId));
        return getDialect(apiParam);
    }

    private Dialect getDialect(ApiParam apiParam) {
        Dialect dialect;
        try {
            Class dialectClazz = Class.forName("com.mesalab.qgw.dialect." +
                    CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, apiParam.getDbType()) + "Dialect");
            Constructor constructor = dialectClazz.getConstructor(ApiParam.class);
            dialect = (Dialect) constructor.newInstance(apiParam);
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.error("Dialect conversion instance exception:{}", e);
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), QGWErrorCode.SQL_SYNTAX_PARSE_EXCEPTION.getCode(),
                    String.format(QGWErrorCode.SQL_SYNTAX_PARSE_EXCEPTION.getMessage(),e.getMessage()));
        }
        return dialect;
    }

    private String parseDBType(String queryId) {
        for (DBTypeEnum dbTypeEnum : DBTypeEnum.values()) {
            if (queryId.startsWith(DigestUtil.md5Hex(dbTypeEnum.getValue()).concat(queryIdSeparator))) {
                return dbTypeEnum.getValue();
            }
        }
        if (queryId.contains(queryIdSeparator)) {
            throw new QGWBusinessException(ResultStatusEnum.NOT_FOUND.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),QGWMessageConst.QUERY_ID_IS_NOT_SUPPORTED));
        }
        return DBTypeEnum.CLICKHOUSE.getValue();
    }


    private boolean logTypeValid(List<StorageDeletionInfo> list) {
        for (StorageDeletionInfo info : list) {
            String value = LogType.getValueByLabel(info.getLogType());
            if (StringUtil.isEmpty(value)) {
                return false;
            }
        }
        return true;
    }

    /**
     * @Description 涉及对ALL、LogType(value，label)处理
     * @Param list:
     * @return: java.util.List<com.mesalab.qgw.model.job.StorageDeletionInfo>
     * @Date: 2021/1/4 11:36 上午
     * @Created by wWei
     */
    private List<StorageDeletionInfo> preProcessOfLogType(List<StorageDeletionInfo> list) {
        if (list.size() == 1 && LogType.ALL.getValue().equalsIgnoreCase(list.get(0).getLogType())) {
            StorageDeletionInfo deletionInfo = list.get(0);
            Integer maxDays = deletionInfo.getMaxDays();
            Integer defaultMaxDays = deletionInfo.getDefaultMaxDays();

            list.clear();
            list.add(new StorageDeletionInfo(LogType.TRAFFIC_LOGS.getValue(), maxDays, defaultMaxDays));
            list.add(new StorageDeletionInfo(LogType.REPORT_AND_METRICS.getValue(), maxDays, defaultMaxDays));
            list.add(new StorageDeletionInfo(LogType.FILES.getValue(), maxDays, defaultMaxDays));
            return list;
        }
        list.forEach(o -> o.setLogType(LogType.getValueByLabel(o.getLogType())));
        return list;
    }

    private boolean jobIsBusy(List<StorageDeletionInfo> list) {
        setCookie();
        for (StorageDeletionInfo info : list) {

            String handler = getDeletePartHandlerByLogType(info.getLogType());
            if (jobIsBusyByHandler(handler)) {
                return true;
            }
            handler = getDeleteAllHandlerByLogType(info.getLogType());
            if (jobIsBusyByHandler(handler)) {
                return true;
            }
        }
        return false;
    }

    private boolean jobIsBusyByHandler(String handler) {
        Map dataByHandler = getDataByHandler(handler);
        String id = String.valueOf(dataByHandler.get("id"));
        BaseResult result = queryJobStatusByJobId(Integer.parseInt(id));
        if (result.getStatus().equals(ResultStatusEnum.LOCKED.getCode())) {
            return true;
        } else if (!result.getStatus().equals(ResultStatusEnum.SUCCESS.getCode())) {
            throw new QGWBusinessException(ResultStatusEnum.SERVER_ERROR.getCode(),ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(),
                    String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(),result.getMessage()));
        }
        return false;
    }


    /**
     * 执行日志删除任务: 调用调度任务任务
     *
     * @param info
     * @return
     */
    private BaseResult executeDeleteStorageJob(StorageDeletionInfo info) {

        BaseResult baseResult;
        String jobHandler;
        String logType = info.getLogType();
        ExecutorParam executorParam = new ExecutorParam();
        if (0 == info.getMaxDays()) {
            jobHandler = getDeleteAllHandlerByLogType(logType);
            Map dataByHandler = getDataByHandler(jobHandler);

            StorageDeletionInfo storageDeletionInfoByHandler = getStorageDeletionInfoByHandler(logType, getDeletePartHandlerByLogType(logType));
            executorParam.setMaxDays(storageDeletionInfoByHandler.getMaxDays());
            XxlJobInfo xxlJobInfo = setXxlJobInfoParam(dataByHandler, executorParam);
            baseResult = executeTriggerAndUpdate(xxlJobInfo);
        } else {
            jobHandler = getDeletePartHandlerByLogType(info.getLogType());
            executorParam.setMaxDays(info.getMaxDays());
            Map oldData = getDataByHandler(jobHandler);
            XxlJobInfo newData = setXxlJobInfoParam(oldData, executorParam);
            baseResult = executeManageJob("update", newData);
            if (baseResult.isSuccess() && LogType.TRAFFIC_LOGS.getValue().equalsIgnoreCase(info.getLogType())) {
                updateSchema(info, oldData);
            }
            if (baseResult.isSuccess()) {
                baseResult = executeManageJob("trigger", newData);
            }
        }
        if (!baseResult.getStatus().equals(ResultStatusEnum.SUCCESS.getCode())) {
            throw new QGWBusinessException(ResultStatusEnum.SERVER_ERROR.getCode(),ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(),
                    String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(),QGWMessageConst.SCHEDULED_TASK_ERROR));
        }
        return baseResult;
    }

    private void updateSchema(StorageDeletionInfo info, Map dataByHandler) {
        Map<String, Map> previous = Maps.newHashMap();
        try {
            Map schemaInfo = metadataService.getSchemaInfo(MetadataTypeEnum.TABLES.getValue(), clickHouseHttpSource.getDbName());
            Object tables = schemaInfo.get("symbols");
            if (StringUtil.isEmpty(tables)) {
                return;
            }
            long ttl = 24 * 60 * 60 * info.getMaxDays();
            List<String> list = (List) tables;
            for (String tableName : list) {
                Map schemaMap = metadataService.getSchemaInfo(MetadataTypeEnum.FIELDS.getValue(), tableName);
                List<Object> schemaDocTTL = JsonPath.read(schemaMap, "$.[?(@.doc.ttl != null)].doc.ttl");
                List<Object> fieldDocTTL = JsonPath.read(schemaMap, "$.fields[?(@.doc.ttl != null)].doc.ttl");
                if (schemaDocTTL.isEmpty() && fieldDocTTL.isEmpty()) {
                    continue;
                }
                previous.put(tableName, metadataService.getSchemaInfo(MetadataTypeEnum.FIELDS.getValue(), tableName));
                Object schemaDoc = schemaMap.get("doc");
                Map<String, Object> map = Maps.newHashMap();
                if (StringUtil.isNotEmpty(schemaDoc)) {
                    map = (Map<String, Object>) schemaDoc;
                }
                if (StringUtil.isNotEmpty(map.get("ttl")) && ttl < Long.parseLong(map.get("ttl").toString())) {
                    map.put("ttl", ttl);
                    schemaMap.put("doc", map);
                }
                List<Map<String, Object>> fields = JsonPath.read(schemaMap, "$.fields");
                List<String> indexKey = metadataService.getIndexKey(tableName);
                for (Map<String, Object> field : fields) {
                    Object doc = field.get("doc");
                    if (StringUtil.isEmpty(doc)) {
                        doc = Maps.newHashMap();
                    }
                    Map<String, Object> fieldDoc = (Map<String, Object>) doc;
                    if (indexKey.contains(field.get("name").toString())) {
                        fieldDoc.put("ttl", null);
                        field.put("doc", fieldDoc);
                        continue;
                    }
                    if (StringUtil.isNotEmpty(fieldDoc.get("ttl")) && ttl < Long.parseLong(fieldDoc.get("ttl").toString())) {
                        fieldDoc.put("ttl", ttl);
                        field.put("doc", fieldDoc);
                    }
                }
                metadataService.updateSchema(tableName, schemaMap);
            }
        } catch (RuntimeException ex) {
            for (String tableName : previous.keySet()) {
                metadataService.updateSchema(tableName, previous.get(tableName));
            }
            executeManageJob("update", mapToBean(dataByHandler, XxlJobInfo.class));
            throw new QGWBusinessException(ResultStatusEnum.SERVER_ERROR.getCode(),
                    ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(),
                    String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(),"up schema error in storage set ", ex.getMessage()));
        }
    }

    private String getDeleteAllHandlerByLogType(String logType) {
        String jobHandler = StringUtil.EMPTY;
        if (LogType.TRAFFIC_LOGS.getValue().equalsIgnoreCase(logType)) {
            jobHandler = JobHandlerEnum.DELETE_ALL_TRAFFIC_DATA_JOB_HANDLER.getValue();
        } else if (LogType.REPORT_AND_METRICS.getValue().equalsIgnoreCase(logType)) {
            jobHandler = JobHandlerEnum.DELETE_ALL_REPORT_AND_METRICS_DATA_JOB_HANDLER.getValue();
        } else if (LogType.FILES.getValue().equalsIgnoreCase(logType)) {
            jobHandler = JobHandlerEnum.DELETE_ALL_FILES_JOB_HANDLER.getValue();
        }
        return jobHandler;
    }

    private String getDeletePartHandlerByLogType(String logType) {
        String jobHandler = StringUtil.EMPTY;
        if (LogType.TRAFFIC_LOGS.getValue().equalsIgnoreCase(logType)) {
            jobHandler = JobHandlerEnum.DELETE_TRAFFIC_DATA_JOB_HANDLER.getValue();
        } else if (LogType.REPORT_AND_METRICS.getValue().equalsIgnoreCase(logType)) {
            jobHandler = JobHandlerEnum.DELETE_REPORT_AND_METRICS_DATA_JOB_HANDLER.getValue();
        } else if (LogType.FILES.getValue().equalsIgnoreCase(logType)) {
            jobHandler = JobHandlerEnum.DELETE_FILES_JOB_HANDLER.getValue();
        }
        return jobHandler;
    }

    /**
     * 通过handler获取数据配额设置状态
     *
     * @param logType
     * @param jobHandlerValue
     * @return
     */
    private StorageDeletionInfo getStorageDeletionInfoByHandler(String logType, String jobHandlerValue) {
        Map trafficDate = getDataByHandler(jobHandlerValue);
        XxlJobInfo xxlJobInfo = mapToBean(trafficDate, XxlJobInfo.class);
        StorageDeletionInfo executorParam = JsonMapper.fromJsonString(xxlJobInfo.getExecutorParam(), StorageDeletionInfo.class);
        executorParam.setLogType(logType);
        return executorParam;
    }

    /**
     * 触发执行器并更新任务
     *
     * @param xxlJobInfo
     * @return
     */
    private BaseResult executeTriggerAndUpdate(XxlJobInfo xxlJobInfo) {
        BaseResult baseResult;
        BaseResult resultExecute = executeManageJob("trigger", xxlJobInfo);
        if (resultExecute.getStatus().equals(ResultStatusEnum.SUCCESS.getCode())) {
            BaseResult resultUpdate = executeManageJob("update", xxlJobInfo);
            if (resultUpdate.getStatus().equals(ResultStatusEnum.SUCCESS.getCode())) {
                baseResult = BaseResultGenerator.success("ok", null);
            } else {
                baseResult = BaseResultGenerator.failure(ResultStatusEnum.SERVER_ERROR.getCode(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(),
                        String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(),"Update task failed:" + xxlJobInfo.getExecutorHandler()));
            }
        } else {
            baseResult = BaseResultGenerator.failure(ResultStatusEnum.SERVER_ERROR.getCode(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(),
                    String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(),"Failed to execute task:" + xxlJobInfo.getExecutorHandler()));
        }
        return baseResult;
    }

    /**
     * 查询任务状态
     *
     * @param jobId
     * @return
     */
    private BaseResult queryJobStatusByJobId(int jobId) {
        BaseResult baseResult = null;
        StringBuilder url = new StringBuilder(jobAdminHttpSource.getUrl()).append("/jobinfo/jobBeat?jobId=").append(jobId);
        Map<String, String> resultMap = httpClientService.httpGet(url.toString(), headers, httpConfig.getServerResponseTimeOut());
        baseResult = resultEncapsulationOfJob(resultMap);
        return baseResult;
    }

    /**
     * 执行调度任务结果封装
     *
     * @param resultMap
     * @return
     */
    private BaseResult resultEncapsulationOfJob(Map<String, String> resultMap) {
        BaseResult baseResult;
        if (StringUtil.isEmpty(resultMap)) {
            baseResult = BaseResultGenerator.failure(ResultStatusEnum.SERVER_ERROR.getCode(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(),
                    String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(),"The service is busy, please contact the scheduling platform!"));
        } else {
            if (resultMap.get("status").equals(String.valueOf(ResultStatusEnum.SUCCESS.getCode()))) {
                Map result = (Map) com.zdjizhi.utils.JsonMapper.fromJsonString(String.valueOf(resultMap.get("result")), Map.class);
                if (result.get("code").equals(ResultStatusEnum.SUCCESS.getCode())) {
                    baseResult = BaseResultGenerator.success("ok", null);
                } else if (result.get("code").equals(ResultStatusEnum.LOCKED.getCode())) {
                    baseResult = BaseResultGenerator.failure(ResultStatusEnum.LOCKED.getCode(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(),
                            String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(), "Clear Task is Running."));
                } else {
                    baseResult = BaseResultGenerator.failure(ResultStatusEnum.SERVER_ERROR.getCode(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(),
                            String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(), result.get("msg")));
                }
            } else {
                baseResult = BaseResultGenerator.failure(ResultStatusEnum.SERVER_ERROR.getCode(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(),
                        String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(), "Clear Task Failed."));
            }
        }
        return baseResult;
    }


    /**
     * 执行调度平台任务更新
     *
     * @param executeType
     * @param xxlJobInfo
     * @return
     */
    private BaseResult executeManageJob(String executeType, XxlJobInfo xxlJobInfo) {
        BaseResult baseResult = null;
        String params = getUrlParamsByMap(getObjectToMap(xxlJobInfo));
        String url = String.format("%s/jobinfo/%s/?%s", jobAdminHttpSource.getUrl(), executeType, params);
        Map<String, String> resultMap = httpClientService.httpGet(url, headers, httpConfig.getServerResponseTimeOut());
        log.warn("请求调度任务" + executeType + "接口" + url);
        baseResult = resultEncapsulationOfJob(resultMap);
        return baseResult;
    }


    /**
     * 获取调度平台Cookie
     *
     * @return
     */
    public void setCookie() {
        StringBuilder url = new StringBuilder(jobAdminHttpSource.getUrl());
        headers.put("Content-Type", "application/json");
        String urlParamsByMap = getUrlParamsByMap(getObjectToMap(jobAdminHttpSource));
        int socketTimeOut = httpConfig.getServerResponseTimeOut();
        Map httpPostResponseHeads = httpClientService.getHttpPostResponseHeads(url + "/login?" + urlParamsByMap, headers, socketTimeOut);
        String cookie = String.valueOf(httpPostResponseHeads.get("SET-COOKIE"));
        headers.put("Cookie", cookie);
    }

    /**
     * 通过handler Value获取jobInfo数据
     *
     * @param handlerValue
     * @return
     */
    private Map getDataByHandler(String handlerValue) {
        StringBuilder url = new StringBuilder(jobAdminHttpSource.getUrl()).
                append("/jobinfo/pageList?jobGroup=-1&triggerStatus=-1&executorHandler=").
                append(handlerValue);
        Map<String, String> resultPageList = httpClientService.httpGet(url.toString(), headers, httpConfig.getServerResponseTimeOut());
        if (StringUtil.isNotEmpty(resultPageList) && resultPageList.get("status").equals(String.valueOf(ResultStatusEnum.SUCCESS.getCode()))) {
            Map<String, Object> maps = (Map<String, Object>) JsonMapper.fromJsonString(resultPageList.get("result"), Map.class);
            if (StringUtil.isEmpty(maps)) {
                throw new QGWBusinessException(ResultStatusEnum.SERVER_ERROR.getCode(),ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(),
                        String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(),"Get or package result exception: " + JsonMapper.toJsonString(resultPageList)));
            }
            List<Map> data = (List) maps.get("data");
            if (data.size() >= 1) {
                return data.get(0);
            } else {
                throw new QGWBusinessException(ResultStatusEnum.SERVER_ERROR.getCode(),ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(),
                        String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(),"The scheduled task has no task information matching executorHandler (" + handlerValue + ")"));
            }
        }
        throw new QGWBusinessException(ResultStatusEnum.SERVER_ERROR.getCode(),ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(),
                String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(),"Get scheduled task exception: " + JsonMapper.toJsonString(resultPageList)));
    }

    /**
     * 更新jobInfo
     *
     * @param data
     * @param executorParam
     * @return
     */
    private XxlJobInfo setXxlJobInfoParam(Map data, ExecutorParam executorParam) {
        XxlJobInfo xxlJobInfo = mapToBean(data, XxlJobInfo.class);
        ExecutorParam executor = JsonMapper.fromJsonString(xxlJobInfo.getExecutorParam(), ExecutorParam.class);
        executorParam.setDefaultMaxDays(executor.getDefaultMaxDays());
        xxlJobInfo.setExecutorParam(JsonMapper.toJsonString(executorParam));
        return xxlJobInfo;
    }

    public static String getUrlParamsByMap(Map<String, Object> map) {
        if (map == null) {
            return "";
        }
        StringBuffer sb = new StringBuffer();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            sb.append(entry.getKey() + "=" + Encodes.urlEncode(String.valueOf(entry.getValue())));
            sb.append("&");
        }
        String s = sb.toString();
        if (s.endsWith("&")) {
            s = StringUtil.substringBeforeLast(s, "&");
        }
        return s;
    }

    public static <T> T mapToBean(Map<String, Object> map, Class<T> clazz) {
        T bean = null;
        try {
            bean = clazz.newInstance();
        } catch (InstantiationException e) {
            log.error("Instantiation Exception: ", e);
        } catch (IllegalAccessException e) {
            log.error("Illegal Access Exception: ", e);
        }
        BeanMap beanMap = BeanMap.create(bean);
        beanMap.putAll(map);
        return bean;
    }

    public static Map<String, Object> getObjectToMap(Object obj) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        Class<?> clazz = obj.getClass();
        Field[] declaredFields = clazz.getDeclaredFields();
        AccessibleObject.setAccessible(declaredFields, true);
        for (Field field : declaredFields) {
            String fieldName = field.getName();
            Object value = null;
            try {
                value = field.get(obj);
            } catch (IllegalAccessException e) {
                log.error("Illegal Access Exception: ", e);
            }
            if (value == null) {
                value = "";
            }
            map.put(fieldName, value);
        }
        return map;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.env = environment;
    }


    @Getter
    @AllArgsConstructor
    enum LogType {
        TRAFFIC_LOGS("Traffic Logs", "Traffic Logs"),
        REPORT_AND_METRICS("Report and Metrics", "Metrics"),
        FILES("Files", "Files"),
        ALL("ALL", "ALL");
        private final String value;
        private final String label;


        public static String getValueByLabel(String label) {
            for (LogType enums : LogType.values()) {
                if (enums.getLabel().equals(label)) {
                    return enums.getValue();
                }
            }
            return "";
        }

        public static String getLabelByValue(String value) {
            for (LogType enums : LogType.values()) {
                if (enums.getValue().equals(value)) {
                    return enums.getLabel();
                }
            }
            return "";
        }
    }
}
