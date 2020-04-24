package com.mesalab.qgw.service.impl;

import com.google.common.collect.Maps;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.BaseResultEnum;
import com.mesalab.common.enums.JobHandlerEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.qgw.model.job.ExecutorParam;
import com.mesalab.qgw.model.job.StorageDeletionInfo;
import com.mesalab.qgw.model.job.XxlJobInfo;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.JobAdminHttpSource;
import com.mesalab.qgw.service.IAPIService;
import com.mesalab.qgw.service.SystemService;
import com.zdjizhi.utils.DateUtils;
import com.zdjizhi.utils.Encodes;
import com.zdjizhi.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cglib.beans.BeanMap;
import org.apache.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.*;

@Slf4j
@Service("systemService")
public class SystemServiceImpl implements SystemService {


    @Autowired
    private IAPIService apiService;
    @Autowired
    private JobAdminHttpSource jobAdminHttpSource;
    @Autowired
    private HttpClientService httpClientService;

    private Map<String, String> headers = Maps.newHashMap();

    @Override
    public BaseResult getStorageQuota() {

        //封装sql进行查询：Analytic Logs、Files、Traffic Logs
        ApiParam param = new ApiParam();
        param.setQuery("(select log_type, used_size, max_size, TIME_FORMAT(MILLIS_TO_TIMESTAMP(1000 * first_storage),'YYYY-MM-dd') as first_storage from sys_storage_log where log_type = 'Report and Metrics' ORDER BY __time desc limit 1)\n" +
                "UNION ALL\n" +
                "(select log_type, used_size, max_size, TIME_FORMAT(MILLIS_TO_TIMESTAMP(1000 * first_storage),'YYYY-MM-dd') as first_storage from sys_storage_log where log_type = 'Files' ORDER BY __time desc limit 1)\n" +
                "UNION ALL\n" +
                "(select log_type, used_size, max_size,TIME_FORMAT(MILLIS_TO_TIMESTAMP(1000 * first_storage),'YYYY-MM-dd') as first_storage from sys_storage_log where log_type = 'Traffic Logs' ORDER BY __time desc limit 1)");
        BaseResult result = apiService.executeQuery(param);
        return result;
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
            String sql  = String.format("select TIME_FORMAT(__time,'YYYY-MM-dd') as stat_time,log_type as type, sum(aggregate_size) as used_size from sys_storage_log where __time >= '%s' and __time < '%s' group by TIME_FORMAT(__time,'YYYY-MM-dd'), log_type", searchStartTime, searchEndTime);
            param.setQuery(sql);
            return apiService.executeQuery(param);
        } else {
            throw  new BusinessException(BaseResultEnum.FAIL.getCode(), "参数校验：start_time 与 end_time 需同时填写", null);
        }
    }

    @Override
    public BaseResult deleteStorage(List<StorageDeletionInfo> list) {

        if (!logTypeIsAvaliable(list)) {
            return BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, "匹配失败, 请检查日志类型!");
        }

        preProcessOfLogType(list);

        if (jobIsBusy(list)) {
            return BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, "任务繁忙中, 请稍后尝试!");
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
        List list = new ArrayList<>();
        if (StringUtil.isBlank(logType)) {

            StorageDeletionInfo trafficInfo = getStorageDeletionInfoByHandler(LogType.TRAFFIC_LOGS.getValue(), JobHandlerEnum.DELETE_TRAFFIC_DATA_JOB_HANDLER.getValue());
            list.add(trafficInfo);

            StorageDeletionInfo reportInfo = getStorageDeletionInfoByHandler(LogType.REPORT_AND_METRICS.getValue(), JobHandlerEnum.DELETE_REPORT_AND_METRICS_DATA_JOB_HANDLER.getValue());
            list.add(reportInfo);

            StorageDeletionInfo storageDeletionInfo = new StorageDeletionInfo();
            storageDeletionInfo.setLogType(LogType.FILES.getValue());//伪代码
            storageDeletionInfo.setMaxDays(365);
            list.add(storageDeletionInfo);
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
        return BaseResultGenerator.success("ok", list);
    }


    private boolean logTypeIsAvaliable(List<StorageDeletionInfo> list) {
        List<LogType> logTypes = Arrays.asList(LogType.values());
        List<String> logType = new ArrayList<>();
        for (LogType type : logTypes) {
            logType.add(type.getValue().toUpperCase());
        }
        for (StorageDeletionInfo info : list) {
            if (!logType.contains(info.getLogType().toUpperCase())) {
                return false;
            }
        }
        return true;
    }

    private List<StorageDeletionInfo> preProcessOfLogType(List<StorageDeletionInfo> list) {
        if (list.size() == 1 && LogType.ALL.getValue().equalsIgnoreCase(list.get(0).getLogType())) {
            Integer maxDays = list.get(0).getMaxDays();

            list.clear();
            list.add(new StorageDeletionInfo(LogType.TRAFFIC_LOGS.getValue(), maxDays));
            list.add(new StorageDeletionInfo(LogType.REPORT_AND_METRICS.getValue(), maxDays));
            list.add(new StorageDeletionInfo(LogType.FILES.getValue(), maxDays));
        }
        return list;
    }

    private boolean jobIsBusy(List<StorageDeletionInfo> list) {
        setCookie();
        for (StorageDeletionInfo info : list) {

            if (LogType.FILES.getValue().equalsIgnoreCase(info.getLogType())) { //伪代码
                continue;
            }
            String handler = getDeletePartHandlerByLogType(info.getLogType());
            Map dataByHandler = getDataByHandler(handler);
            Object id = dataByHandler.get("id");
            BaseResult result = queryJobStatusByJobId(Integer.parseInt(String.valueOf(id)));
            if (!result.getStatus().equals(HttpStatus.SC_OK)) {
                return true;
            }
            handler = getDeleteAllHandlerByLogType(info.getLogType());
            dataByHandler = getDataByHandler(handler);
            id = dataByHandler.get("id");
            result = queryJobStatusByJobId(Integer.parseInt(String.valueOf(id)));
            if (!result.getStatus().equals(HttpStatus.SC_OK)) {
                return true;
            }
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

        if (LogType.FILES.getValue().equalsIgnoreCase(info.getLogType())) { //伪代码
            return BaseResultGenerator.failure(HttpStatus.SC_OK, "ok");
        }

        BaseResult baseResult;
        String jobHandler;
        String logType = info.getLogType();
        ExecutorParam executorParam = new ExecutorParam();
        if (0 == info.getMaxDays()) {
            jobHandler = getDeleteAllHandlerByLogType(logType);
            Map dataByHandler = getDataByHandler(jobHandler);

            if (LogType.TRAFFIC_LOGS.getValue().equalsIgnoreCase(logType)) {
                executorParam.setMaxdays(30);
            } else if (LogType.REPORT_AND_METRICS.getValue().equalsIgnoreCase(logType)) {
                executorParam.setMaxdays(365);
            } else if (LogType.FILES.getValue().equalsIgnoreCase(logType)) {
                executorParam.setMaxdays(365);
            }
            XxlJobInfo xxlJobInfo = setXxlJobInfoParam(dataByHandler, executorParam);
            baseResult = executeTriggerAndUpdate(xxlJobInfo);

            if (baseResult.getStatus().equals(HttpStatus.SC_OK)) {
                String deletePartHandler = getDeletePartHandlerByLogType(info.getLogType());
                Map date = getDataByHandler(deletePartHandler);
                XxlJobInfo xxlJobInfoOfUpdate = setXxlJobInfoParam(date, executorParam);
                baseResult = executeManageJob("update", xxlJobInfoOfUpdate);
            }
        } else {
            jobHandler = getDeletePartHandlerByLogType(info.getLogType());
            executorParam.setMaxdays(info.getMaxDays());
            Map dataByHandler = getDataByHandler(jobHandler);
            XxlJobInfo xxlJobInfo = setXxlJobInfoParam(dataByHandler, executorParam);
            baseResult = executeTriggerAndUpdate(xxlJobInfo);
        }
        if (!baseResult.getStatus().equals(HttpStatus.SC_OK)) {
            throw new BusinessException(BaseResultEnum.SERVER_ERROR.getCode(), "调度任务数据配额设置任务执行失败", null);
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
                baseResult = BaseResultGenerator.success("OK", null);
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
        StringBuilder url = new StringBuilder(jobAdminHttpSource.getUrl()).
                append("/jobinfo/jobBeat?jobId=").
                append(jobId);
        Map<String, String> resultMap = httpClientService.httpGet(url.toString(), headers);
        log.info("请求调度任务jobBeat接口" + url);
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
                } else {
                    baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, "服务繁忙，请稍后再试: " + result.get("msg"));
                }
            } else {
                baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, resultMap.get("msg"));
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
        Map<String, String> resultMap = httpClientService.httpGet(url, headers);
        log.info("请求调度任务" + executeType + "接口" + url);
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
        Map httpPostResponseHeads = httpClientService.getHttpPostResponseHeads(url + "/login?" + urlParamsByMap, headers);
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
        Map<String, String> resultPageList = httpClientService.httpGet(url.toString(), headers);
        log.info("请求调度任务pageList接口 " + url);
        if (StringUtil.isNotEmpty(resultPageList) && resultPageList.get("status").equals(String.valueOf(HttpStatus.SC_OK))) {
            Map<String, Object> maps = (Map<String, Object>) JsonMapper.fromJsonString(resultPageList.get("result"), Map.class);
            if (StringUtil.isEmpty(maps)) {
                throw new BusinessException(BaseResultEnum.SERVER_ERROR.getCode(), "获取或封装结果异常: " + resultPageList, null);
            }
            List<Map> data = (List) maps.get("data");
            if (data.size() >= 1) {
                return data.get(0);
            } else {
                throw new BusinessException(BaseResultEnum.SERVER_ERROR.getCode(), "调度任务无匹配executorHandler(" + handlerValue + ")的任务信息", null);
            }
        }
        throw new BusinessException(BaseResultEnum.SERVER_ERROR.getCode(), "获取调度任务异常: " + resultPageList, null);
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
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
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
                e.printStackTrace();
            }
            if (value == null) {
                value = "";
            }
            map.put(fieldName, value);
        }
        return map;
    }


    enum LogType {
        TRAFFIC_LOGS("Traffic Logs"), REPORT_AND_METRICS("Report and Metrics"), FILES("Files"), ALL("ALL");
        private final String value;

        LogType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
