package com.mesalab.qgw.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson2.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayway.jsonpath.JsonPath;
import com.mesalab.common.configuration.HttpConfig;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.*;
import com.mesalab.common.httpClient.HttpClientService;
import com.mesalab.common.utils.sqlparser.SQLHelper;
import com.mesalab.common.exception.QGWMessageConst;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.qgw.model.basic.ClickHouseHttpSource;
import com.mesalab.qgw.model.basic.QueryContext;
import com.mesalab.qgw.model.basic.JobAdminHttpSource;
import com.mesalab.qgw.model.job.EncryptionInfo;
import com.mesalab.qgw.model.job.ExecutorParam;
import com.mesalab.qgw.model.job.StorageDeletionInfo;
import com.mesalab.qgw.model.job.XxlJobInfo;
import com.mesalab.qgw.service.SQLSyncQueryService;
import com.mesalab.qgw.service.DatabaseService;
import com.mesalab.qgw.service.SystemService;
import com.mesalab.services.common.property.SqlPropertySourceFactory;
import com.geedgenetworks.utils.DateUtils;
import com.geedgenetworks.utils.Encodes;
import com.geedgenetworks.utils.StringUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jasypt.util.text.BasicTextEncryptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cglib.beans.BeanMap;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.util.*;
import java.util.regex.Pattern;

@Service("systemService")
@PropertySource(value = "classpath:http-sql-template.sql", factory = SqlPropertySourceFactory.class)
public class SystemServiceImpl implements SystemService, EnvironmentAware {
    private static final Log log = LogFactory.get();
    private static Pattern pTTL = Pattern.compile(".*toIntervalSecond\\((\\d+)\\)", Pattern.CASE_INSENSITIVE);
    private Environment env;
    @Autowired
    private SQLSyncQueryService sqlSyncQueryService;
    @Autowired
    private JobAdminHttpSource jobAdminHttpSource;
    @Autowired
    private HttpClientService httpClientService;
    @Resource(name = "databaseService")
    private DatabaseService databaseService;
    @Autowired
    HttpConfig httpConfig;
    @Autowired
    ClickHouseHttpSource clickHouseHttpSource;

    public final static String queryIdSeparator = ":";

    private Map<String, String> headers = Maps.newHashMap();

    @Override
    public BaseResult getStorageQuota() {

        //封装sql进行查询：Analytic Logs、Files、Traffic Logs
        QueryContext param = new QueryContext();
        param.setQuery(env.getProperty("SYSTEM_STORAGE_QUOTA"));
        BaseResult result = sqlSyncQueryService.executeQuery(param);
        return resetBaseResult(result, "type");
    }

    @Override
    public BaseResult dailyTrendOfStorage(String searchStartTime, String searchEndTime) {

        Date currentDate = DateUtils.convertStringToDate(DateUtils.getCurrentDate(), DateUtils.YYYY_MM_DD);

        if (StringUtil.isBlank(searchStartTime) && StringUtil.isBlank(searchEndTime)) {
            searchStartTime = DateUtils.getFormatDate(DateUtils.getSomeDate(currentDate, -7), DateUtils.YYYY_MM_DD_HH24_MM_SS);
            searchEndTime = DateUtils.getFormatDate(currentDate, DateUtils.YYYY_MM_DD_HH24_MM_SS);
        }

        if (StringUtil.isNotBlank(searchStartTime) && StringUtil.isNotBlank(searchEndTime)) {
            QueryContext param = new QueryContext();
            String sql = String.format(env.getProperty("SYSTEM_DAILY_TREND_OF_STORAGE"), searchStartTime, searchEndTime);
            param.setQuery(sql);
            BaseResult result = sqlSyncQueryService.executeQuery(param);
            List<Map> data = (List<Map>) result.getData();
            data.forEach(o -> o.put("type", LogType.getLabelByValue(String.valueOf(o.get("type")))));
            return result;
        } else {
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.START_TIME_AND_END_TIME_NOT_NULL));
        }
    }

    private BaseResult resetBaseResult(BaseResult result, String alias) {
        if (!result.isSuccess()) {
            return result;
        }
        List<Map> data = (List<Map>) result.getData();
        for (Map map : data){
            String logType = LogType.getLabelByValue(String.valueOf(map.get(alias)));
            BaseResult deleteStorageStatus = getDeleteStorageStatus(String.valueOf(map.get(alias)));
            List<StorageDeletionInfo> data1 = (List<StorageDeletionInfo>) deleteStorageStatus.getData();
            map.put(alias, logType);
            map.put("max_days",data1.get(0).getMaxDays());
            map.put("default_max_days",data1.get(0).getDefaultMaxDays());
        }
        return result;
    }

    @Override
    public BaseResult deleteStorage(List<StorageDeletionInfo> list) {

        if (!logTypeValid(list)) {
            return BaseResultGenerator.failure(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(),"Match failed, please check log type!"));
        }

        preProcessOfLogType(list);

        if (jobIsBusy(list)) {
            return BaseResultGenerator.failure(HttpStatusCodeEnum.LOCKED.getCode(),  CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(),"The task is busy, please try later!"));
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

            StorageDeletionInfo trafficInfo = getStorageDeletionInfoByHandler(LogType.TRAFFIC_LOGS.getValue(), JobHandlerOption.DELETE_TRAFFIC_DATA_JOB_HANDLER.getValue());
            list.add(trafficInfo);

            StorageDeletionInfo reportInfo = getStorageDeletionInfoByHandler(LogType.REPORT_AND_METRICS.getValue(), JobHandlerOption.DELETE_REPORT_AND_METRICS_DATA_JOB_HANDLER.getValue());
            list.add(reportInfo);

            StorageDeletionInfo fileInfo = getStorageDeletionInfoByHandler(LogType.FILES.getValue(), JobHandlerOption.DELETE_FILES_JOB_HANDLER.getValue());
            list.add(fileInfo);
        } else if (LogType.TRAFFIC_LOGS.getValue().equalsIgnoreCase(logType)) {
            StorageDeletionInfo trafficInfo = getStorageDeletionInfoByHandler(logType, JobHandlerOption.DELETE_TRAFFIC_DATA_JOB_HANDLER.getValue());
            list.add(trafficInfo);
        } else if (LogType.REPORT_AND_METRICS.getValue().equalsIgnoreCase(logType)) {
            StorageDeletionInfo reportInfo = getStorageDeletionInfoByHandler(logType, JobHandlerOption.DELETE_REPORT_AND_METRICS_DATA_JOB_HANDLER.getValue());
            list.add(reportInfo);
        } else if (LogType.FILES.getValue().equalsIgnoreCase(logType)) {
            StorageDeletionInfo reportInfo = getStorageDeletionInfoByHandler(logType, JobHandlerOption.DELETE_FILES_JOB_HANDLER.getValue());
            list.add(reportInfo);
        } else {
            return BaseResultGenerator.failure(HttpStatusCodeEnum.SERVER_ERROR.getCode(), "No matching log type: " + logType);
        }
        list.forEach(o -> o.setType(LogType.getLabelByValue(o.getType())));
        return BaseResultGenerator.success("ok", list);
    }


    @Override
    public String getCustomQueryId(String resultId, String query) {
        String tableName = SQLHelper.getTableName(query).get(0);
        String dbType = databaseService.getDBEngineByTableName(tableName);
        return DigestUtil.md5Hex(dbType) + queryIdSeparator + DigestUtil.md5Hex(resultId + query.trim());
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




    private boolean logTypeValid(List<StorageDeletionInfo> list) {
        for (StorageDeletionInfo info : list) {
            String value = LogType.getValueByLabel(info.getType());
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
        if (list.size() == 1){
            StorageDeletionInfo deletionInfo = list.get(0);
            Integer maxDays = deletionInfo.getMaxDays();
            Integer defaultMaxDays = deletionInfo.getDefaultMaxDays();
            if (LogType.ALL.getValue().equalsIgnoreCase(list.get(0).getType())){
                list.clear();
                list.add(new StorageDeletionInfo(LogType.TRAFFIC_LOGS.getValue(), maxDays, defaultMaxDays));
                list.add(new StorageDeletionInfo(LogType.REPORT_AND_METRICS.getValue(), maxDays, defaultMaxDays));
                list.add(new StorageDeletionInfo(LogType.FILES.getValue(), maxDays, defaultMaxDays));
                return list;
            }else if (LogType.TRAFFIC_LOGS.getValue().equalsIgnoreCase(list.get(0).getType()) |
                      LogType.FILES.getValue().equalsIgnoreCase(list.get(0).getType()) |
                      LogType.REPORT_AND_METRICS.getLabel().equalsIgnoreCase(list.get(0).getType())) {
                list.clear();
                list.add(new StorageDeletionInfo(LogType.getValueByLabel(deletionInfo.getType()), maxDays, defaultMaxDays));
                return list;
            }
        }
        list.forEach(o -> o.setType(LogType.getValueByLabel(o.getType())));
        return list;
    }

    private boolean jobIsBusy(List<StorageDeletionInfo> list) {
        setCookie();
        for (StorageDeletionInfo info : list) {

            String handler = getDeletePartHandlerByLogType(info.getType());
            if (jobIsBusyByHandler(handler)) {
                return true;
            }
            handler = getDeleteAllHandlerByLogType(info.getType());
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
        if (result.getStatus().equals(HttpStatusCodeEnum.LOCKED.getCode())) {
            return true;
        } else if (!result.getStatus().equals(HttpStatusCodeEnum.SUCCESS.getCode())) {
            throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(),result.getMessage()));
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
        String logType = info.getType();
        ExecutorParam executorParam = new ExecutorParam();
        if (0 == info.getMaxDays()) {
            jobHandler = getDeleteAllHandlerByLogType(logType);
            Map dataByHandler = getDataByHandler(jobHandler);

            StorageDeletionInfo storageDeletionInfoByHandler = getStorageDeletionInfoByHandler(logType, getDeletePartHandlerByLogType(logType));
            executorParam.setMaxDays(storageDeletionInfoByHandler.getMaxDays());
            XxlJobInfo xxlJobInfo = setXxlJobInfoParam(dataByHandler, executorParam);
            baseResult = executeTriggerAndUpdate(xxlJobInfo);
        } else {
            jobHandler = getDeletePartHandlerByLogType(info.getType());
            executorParam.setMaxDays(info.getMaxDays());
            Map oldData = getDataByHandler(jobHandler);
            XxlJobInfo newData = setXxlJobInfoParam(oldData, executorParam);
            baseResult = executeManageJob("update", newData);
            if (baseResult.isSuccess() && LogType.TRAFFIC_LOGS.getValue().equalsIgnoreCase(info.getType())) {
                updateSchema(info, oldData);
            }
            if (baseResult.isSuccess()) {
                baseResult = executeManageJob("trigger", newData);
            }
        }
        if (!baseResult.getStatus().equals(HttpStatusCodeEnum.SUCCESS.getCode())) {
            throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(),QGWMessageConst.SCHEDULED_TASK_ERROR));
        }
        return baseResult;
    }

    private void updateSchema(StorageDeletionInfo info, Map dataByHandler) {
        Map<String, Map> previous = Maps.newHashMap();
        try {
            Map schemaInfo = databaseService.getSchemaInfo(MetadataType.TABLES.getValue(), clickHouseHttpSource.getDbName(), false);
            Object tables = schemaInfo.get("symbols");
            if (StringUtil.isEmpty(tables)) {
                return;
            }
            long ttl = 24 * 60 * 60 * info.getMaxDays();
            List<String> list = (List) tables;
            for (String tableName : list) {
                Map schemaMap = databaseService.getSchemaInfo(MetadataType.FIELDS.getValue(), tableName, false);
                List<Object> schemaDocTTL = JsonPath.read(schemaMap, "$.[?(@.doc.ttl != null)].doc.ttl");
                List<Object> fieldDocTTL = JsonPath.read(schemaMap, "$.fields[?(@.doc.ttl != null)].doc.ttl");
                if (schemaDocTTL.isEmpty() && fieldDocTTL.isEmpty()) {
                    continue;
                }
                previous.put(tableName, databaseService.getSchemaInfo(MetadataType.FIELDS.getValue(), tableName, false));
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
                List<String> indexKey = databaseService.getIndexKey(tableName);
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
                databaseService.updateSchema(tableName, schemaMap);
            }
        } catch (RuntimeException ex) {
            for (String tableName : previous.keySet()) {
                databaseService.updateSchema(tableName, previous.get(tableName));
            }
            executeManageJob("update", mapToBean(dataByHandler, XxlJobInfo.class));
            throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(),
                    CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(),"up schema error in storage set ", ex.getMessage()));
        }
    }

    private String getDeleteAllHandlerByLogType(String logType) {
        String jobHandler = StringUtil.EMPTY;
        if (LogType.TRAFFIC_LOGS.getValue().equalsIgnoreCase(logType)) {
            jobHandler = JobHandlerOption.DELETE_ALL_TRAFFIC_DATA_JOB_HANDLER.getValue();
        } else if (LogType.REPORT_AND_METRICS.getValue().equalsIgnoreCase(logType)) {
            jobHandler = JobHandlerOption.DELETE_ALL_REPORT_AND_METRICS_DATA_JOB_HANDLER.getValue();
        } else if (LogType.FILES.getValue().equalsIgnoreCase(logType)) {
            jobHandler = JobHandlerOption.DELETE_ALL_FILES_JOB_HANDLER.getValue();
        }
        return jobHandler;
    }

    private String getDeletePartHandlerByLogType(String logType) {
        String jobHandler = StringUtil.EMPTY;
        if (LogType.TRAFFIC_LOGS.getValue().equalsIgnoreCase(logType)) {
            jobHandler = JobHandlerOption.DELETE_TRAFFIC_DATA_JOB_HANDLER.getValue();
        } else if (LogType.REPORT_AND_METRICS.getValue().equalsIgnoreCase(logType)) {
            jobHandler = JobHandlerOption.DELETE_REPORT_AND_METRICS_DATA_JOB_HANDLER.getValue();
        } else if (LogType.FILES.getValue().equalsIgnoreCase(logType)) {
            jobHandler = JobHandlerOption.DELETE_FILES_JOB_HANDLER.getValue();
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
        StorageDeletionInfo executorParam = JSON.parseObject(StrUtil.toUnderlineCase(xxlJobInfo.getExecutorParam()), StorageDeletionInfo.class);
        executorParam.setType(logType);
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
        if (resultExecute.getStatus().equals(HttpStatusCodeEnum.SUCCESS.getCode())) {
            BaseResult resultUpdate = executeManageJob("update", xxlJobInfo);
            if (resultUpdate.getStatus().equals(HttpStatusCodeEnum.SUCCESS.getCode())) {
                baseResult = BaseResultGenerator.success("ok", null);
            } else {
                baseResult = BaseResultGenerator.failure(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                        String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(),"Update task failed:" + xxlJobInfo.getExecutorHandler()));
            }
        } else {
            baseResult = BaseResultGenerator.failure(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(),"Failed to execute task:" + xxlJobInfo.getExecutorHandler()));
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
            baseResult = BaseResultGenerator.failure(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(),"The service is busy, please contact the scheduling platform!"));
        } else {
            if (resultMap.get("status").equals(String.valueOf(HttpStatusCodeEnum.SUCCESS.getCode()))) {
                Map result = JSON.parseObject(resultMap.get("result"), Map.class);
                if (result.get("code").equals(HttpStatusCodeEnum.SUCCESS.getCode())) {
                    baseResult = BaseResultGenerator.success("ok", null);
                } else if (result.get("code").equals(HttpStatusCodeEnum.LOCKED.getCode())) {
                    baseResult = BaseResultGenerator.failure(HttpStatusCodeEnum.LOCKED.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                            String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(), "Clear Task is Running."));
                } else {
                    baseResult = BaseResultGenerator.failure(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                            String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(), result.get("msg")));
                }
            } else {
                baseResult = BaseResultGenerator.failure(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                        String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(), "Clear Task Failed."));
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
        if (StringUtil.isNotEmpty(resultPageList) && resultPageList.get("status").equals(String.valueOf(HttpStatusCodeEnum.SUCCESS.getCode()))) {
            Map<String, Object> maps = JSON.parseObject(resultPageList.get("result"), Map.class);
            if (StringUtil.isEmpty(maps)) {
                throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                        String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(),"Get or package result exception: " + JSON.toJSONString(resultPageList)));
            }
            List<Map> data = (List) maps.get("data");
            if (data.size() >= 1) {
                return data.get(0);
            } else {
                throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                        String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(),"The scheduled task has no task information matching executorHandler (" + handlerValue + ")"));
            }
        }
        throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(),"Get scheduled task exception: " + JSON.toJSONString(resultPageList)));
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
        ExecutorParam executor = JSON.parseObject(xxlJobInfo.getExecutorParam(), ExecutorParam.class);
        executorParam.setDefaultMaxDays(executor.getDefaultMaxDays());
        xxlJobInfo.setExecutorParam(JSON.toJSONString(executorParam));
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
        ALL("All", "All");
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
