package com.mesalab.qgw.service.impl;

import cn.hutool.core.util.URLUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.collect.Maps;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.JobHandlerEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.knowledge.common.utils.HttpConfig;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.ClickHouseHttpSource;
import com.mesalab.qgw.model.api.JobAdminHttpSource;
import com.mesalab.qgw.model.job.ExecutorParam;
import com.mesalab.qgw.model.job.StorageDeletionInfo;
import com.mesalab.qgw.model.job.XxlJobInfo;
import com.mesalab.qgw.service.ApiService;
import com.mesalab.qgw.service.SystemService;
import com.zdjizhi.utils.DateUtils;
import com.zdjizhi.utils.Encodes;
import com.zdjizhi.utils.StringUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cglib.beans.BeanMap;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.*;

@Service("systemService")
public class SystemServiceImpl implements SystemService {
    private static final Log log = LogFactory.get();
    @Autowired
    private ApiService apiService;
    @Autowired
    private JobAdminHttpSource jobAdminHttpSource;
    @Autowired
    private HttpClientService httpClientService;
    @Autowired
    private ClickHouseHttpSource clickHouseHttpSource;
    @Autowired
    HttpConfig httpConfig;

    private Map<String, String> headers = Maps.newHashMap();

    @Override
    public BaseResult getStorageQuota() {

        //封装sql进行查询：Analytic Logs、Files、Traffic Logs
        ApiParam param = new ApiParam();
        param.setQuery("SELECT log_type, SUM(used_size) as used_size, SUM(max_size) * 7/10 as max_size, TIME_FORMAT(LATEST(last_storage) * 1000,'YYYY-MM-dd') as first_storage \n" +
                "FROM ( \n" +
                "  SELECT log_type, LATEST(used_size) as used_size, LATEST(max_size) as max_size, LATEST(last_storage) as last_storage\n" +
                "  FROM sys_storage_log\n" +
                "  WHERE __time >= CURRENT_TIMESTAMP - INTERVAL '1' HOUR AND data_center != '' \n" +
                "  GROUP BY data_center,log_type\n" +
                ")\n" +
                "GROUP BY log_type");
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
            String sql = String.format("select TIME_FORMAT(__time,'YYYY-MM-dd') as stat_time,log_type as type, sum(aggregate_size) as used_size from sys_storage_log where __time >= '%s' and __time < '%s' group by TIME_FORMAT(__time,'YYYY-MM-dd'), log_type", searchStartTime, searchEndTime);
            param.setQuery(sql);
            BaseResult result = apiService.executeQuery(param);
            return resetBaseResult(result, "type");
        } else {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), "参数校验：start_time 与 end_time 需同时填写", null);
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
            return BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, "匹配失败, 请检查日志类型!");
        }

        preProcessOfLogType(list);

        if (jobIsBusy(list)) {
            return BaseResultGenerator.failure(HttpStatus.SC_LOCKED, "任务繁忙中, 请稍后尝试!");
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
            return BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, "没有匹配的日志类型: " + logType);
        }
        list.forEach(o->o.setLogType(LogType.getLabelByValue(o.getLogType())));
        return BaseResultGenerator.success("ok", list);
    }

    @Override
    public BaseResult deleteQueryTask(String queryId) {
        String executeSql = "kill query where query_id = '" + queryId + "'";
        String queryURL = URLUtil.normalize(clickHouseHttpSource.getUrl() + "/?");

        StringBuilder queryParamBuilder = new StringBuilder("user=")
                .append(clickHouseHttpSource.getLongTermAccount().getUsername()).append("&")
                .append("password=").append(clickHouseHttpSource.getLongTermAccount().getUserKey()).append("&")
                .append("database=").append(clickHouseHttpSource.getDbName()).append("&")
                .append("query=").append(executeSql)
                .append(" FORMAT ").append("JSON").append(" ;");

        List<NameValuePair> values = URLEncodedUtils.parse(queryParamBuilder.toString(), Charset.forName("UTF-8"));

        Map<String, String> map = httpClientService.httpGet(queryURL + URLEncodedUtils.format(values, "utf-8"), 0);
        if (map.get("status").equals(String.valueOf(HttpStatus.SC_OK))) {
            return BaseResultGenerator.success4Message("ok");
        } else {
            return BaseResultGenerator.failure(Integer.parseInt(map.get("status")), map.get("result"));
        }
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
            Integer maxDays = list.get(0).getMaxDays();

            list.clear();
            list.add(new StorageDeletionInfo(LogType.TRAFFIC_LOGS.getValue(), maxDays));
            list.add(new StorageDeletionInfo(LogType.REPORT_AND_METRICS.getValue(), maxDays));
            list.add(new StorageDeletionInfo(LogType.FILES.getValue(), maxDays));
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
        if (result.getStatus().equals(HttpStatus.SC_LOCKED)) {
            return true;
        } else if (!result.getStatus().equals(HttpStatus.SC_OK)) {
            throw new BusinessException(ResultStatusEnum.SERVER_ERROR.getCode(), "获取任务状态异常: " + result.getMessage(), null);
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
            executorParam.setMaxdays(storageDeletionInfoByHandler.getMaxDays());

            XxlJobInfo xxlJobInfo = setXxlJobInfoParam(dataByHandler, executorParam);
            baseResult = executeTriggerAndUpdate(xxlJobInfo);
        } else {
            jobHandler = getDeletePartHandlerByLogType(info.getLogType());
            executorParam.setMaxdays(info.getMaxDays());
            Map dataByHandler = getDataByHandler(jobHandler);
            XxlJobInfo xxlJobInfo = setXxlJobInfoParam(dataByHandler, executorParam);
            baseResult = executeTriggerAndUpdate(xxlJobInfo);
        }
        if (!baseResult.getStatus().equals(HttpStatus.SC_OK)) {
            throw new BusinessException(ResultStatusEnum.SERVER_ERROR.getCode(), "调度任务数据配额设置任务执行失败", null);
        }
        return baseResult;
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
        StorageDeletionInfo storageDeletionInfo = new StorageDeletionInfo();
        Map trafficDate = getDataByHandler(jobHandlerValue);
        XxlJobInfo xxlJobInfo = mapToBean(trafficDate, XxlJobInfo.class);
        Map executorParam = (Map) com.zdjizhi.utils.JsonMapper.fromJsonString(xxlJobInfo.getExecutorParam(), Map.class);
        Object maxDays = executorParam.get("maxdays");
        storageDeletionInfo.setLogType(logType);
        storageDeletionInfo.setMaxDays(Integer.parseInt(String.valueOf(maxDays)));
        return storageDeletionInfo;
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
        if (resultExecute.getStatus().equals(HttpStatus.SC_OK)) {
            BaseResult resultUpdate = executeManageJob("update", xxlJobInfo);
            if (resultUpdate.getStatus().equals(HttpStatus.SC_OK)) {
                baseResult = BaseResultGenerator.success("ok", null);
            } else {
                baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, "更新任务失败: " + xxlJobInfo.getExecutorHandler());
            }
        } else {
            baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, "执行任务失败: " + xxlJobInfo.getExecutorHandler());
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
            baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, "服务繁忙，请联系调度平台方！");
        } else {
            if (resultMap.get("status").equals(String.valueOf(HttpStatus.SC_OK))) {
                Map result = (Map) com.zdjizhi.utils.JsonMapper.fromJsonString(String.valueOf(resultMap.get("result")), Map.class);
                if (result.get("code").equals(HttpStatus.SC_OK)) {
                    baseResult = BaseResultGenerator.success("ok", null);
                } else if (result.get("code").equals(HttpStatus.SC_LOCKED)) {
                    baseResult = BaseResultGenerator.failure(HttpStatus.SC_LOCKED, "Clear Task is Running.");
                } else {
                    baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, String.valueOf(result.get("msg")));
                }
            } else {
                baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Clear Task Failed.");
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
        if (StringUtil.isNotEmpty(resultPageList) && resultPageList.get("status").equals(String.valueOf(HttpStatus.SC_OK))) {
            Map<String, Object> maps = (Map<String, Object>) JsonMapper.fromJsonString(resultPageList.get("result"), Map.class);
            if (StringUtil.isEmpty(maps)) {
                throw new BusinessException(ResultStatusEnum.SERVER_ERROR.getCode(), "获取或封装结果异常: " + resultPageList, null);
            }
            List<Map> data = (List) maps.get("data");
            if (data.size() >= 1) {
                return data.get(0);
            } else {
                throw new BusinessException(ResultStatusEnum.SERVER_ERROR.getCode(), "调度任务无匹配executorHandler(" + handlerValue + ")的任务信息", null);
            }
        }
        throw new BusinessException(ResultStatusEnum.SERVER_ERROR.getCode(), "获取调度任务异常: " + resultPageList, null);
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
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
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
