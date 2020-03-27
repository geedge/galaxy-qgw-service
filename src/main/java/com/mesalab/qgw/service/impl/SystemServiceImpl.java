package com.mesalab.qgw.service.impl;

import com.google.common.collect.Maps;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.configuration.SchemaProperties;
import com.mesalab.common.enums.BaseResultEnum;
import com.mesalab.common.enums.JobHandlerEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.common.utils.SpringContextUtil;
import com.mesalab.qgw.model.job.ExecutorParam;
import com.mesalab.qgw.model.job.StorageDeletionInfo;
import com.mesalab.qgw.model.job.XxlJobInfo;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.ClickHouseHttpSource;
import com.mesalab.qgw.model.api.DruidIoHttpSource;
import com.mesalab.qgw.model.api.JobAdminHttpSource;
import com.mesalab.qgw.service.IAPIService;
import com.mesalab.qgw.service.SystemService;
import com.zdjizhi.utils.DateUtils;
import com.zdjizhi.utils.Encodes;
import com.zdjizhi.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cglib.beans.BeanMap;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.*;

@Slf4j
@Service
public class SystemServiceImpl implements SystemService {


    @Autowired
    IAPIService apiService;


    SchemaProperties schemaProperties = (SchemaProperties) SpringContextUtil.getBean("schemaProperties");
    ClickHouseHttpSource clickHouseHttpSource = (ClickHouseHttpSource) SpringContextUtil.getBean("clickHouseHttpSource");
    JobAdminHttpSource jobAdminHttpSource = (JobAdminHttpSource) SpringContextUtil.getBean("jobAdminHttpSource");
    private DruidIoHttpSource druidIoHttpSource = (DruidIoHttpSource) SpringContextUtil.getBean("druidIoHttpSource");
    private HttpClientService httpClientService = (HttpClientService) SpringContextUtil.getBean("httpClientService");

    private Map<String, String> headers = Maps.newHashMap();

    @Override
    public BaseResult getStorageQuota() {

        //封装sql进行查询：Analytic Logs、Files、Traffic Logs
        ApiParam param = new ApiParam();
        param.setQuery("(select log_type, used_size, max_size, TIME_FORMAT(MILLIS_TO_TIMESTAMP(1000 * first_storage),'YYYY-MM-dd') as first_storage from sys_storage_log where log_type = 'r' ORDER BY __time desc limit 1)\n" +
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
    public BaseResult deleteStorage(String logType, int maxDays) {
        BaseResult baseResult = null;
        setCookie();
        if ("ALL".equals(logType.toUpperCase())) {
            baseResult = deleteAllAndUpdate(maxDays);
        } else {
            baseResult = deleteByMaxDays(logType, maxDays);
        }
        return baseResult;
    }

    @Override
    public BaseResult getDeleteStorageStatus(String logType) {
        setCookie();
        List list = new ArrayList<>();
        if (StringUtil.isBlank(logType)) {

            StorageDeletionInfo trafficInfo = getStorageDeletionInfoByHandler("Traffic Logs", JobHandlerEnum.DELETE_TRAFFIC_DATA_JOB_HANDLER.getValue());
            list.add(trafficInfo);

            StorageDeletionInfo reportInfo = getStorageDeletionInfoByHandler("Report and Metrics", JobHandlerEnum.DELETE_REPORT_AND_METRICS_DATA_JOB_HANDLER.getValue());
            list.add(reportInfo);
        } else if ("Traffic Logs".equalsIgnoreCase(logType)) {
            StorageDeletionInfo trafficInfo = getStorageDeletionInfoByHandler(logType, JobHandlerEnum.DELETE_TRAFFIC_DATA_JOB_HANDLER.getValue());
            list.add(trafficInfo);
        } else if ("Report and Metrics".equalsIgnoreCase(logType)) {
            StorageDeletionInfo reportInfo = getStorageDeletionInfoByHandler(logType, JobHandlerEnum.DELETE_REPORT_AND_METRICS_DATA_JOB_HANDLER.getValue());
            list.add(reportInfo);
        } else {
            return BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, "无匹配的logType");
        }
        return BaseResultGenerator.success("ok", list);
    }

    /**
     * 通过handler获取数据配额设置状态
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

    private BaseResult deleteByMaxDays(String logType, int maxDays) {
        BaseResult baseResult;
        if ("Traffic Logs".equalsIgnoreCase(logType)) {
            Map trafficDate = getDataByHandler(JobHandlerEnum.DELETE_TRAFFIC_DATA_JOB_HANDLER.getValue());
            XxlJobInfo xxlJobInfo = setXxlJobInfoParam(trafficDate, getClickHouseExecutorParam(maxDays));
            BaseResult resultUpdateTraffic = executeManageJob("update", xxlJobInfo);
            BaseResult resultTriggerTraffic = executeManageJob("trigger", xxlJobInfo);

            if (resultUpdateTraffic.getStatus().equals(HttpStatus.SC_OK) && resultTriggerTraffic.getStatus().equals(HttpStatus.SC_OK)) {
                baseResult = BaseResultGenerator.success("ok", null);
            } else {
                String info = String.format("启动删除ClickHouse: %s, 更新删除ClickHouse: %s", resultTriggerTraffic.getStatus(), resultUpdateTraffic.getStatus());
                baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, info);
            }

        } else if ("Report and Metrics".equalsIgnoreCase(logType)) {
            Map trafficDate = getDataByHandler(JobHandlerEnum.DELETE_REPORT_AND_METRICS_DATA_JOB_HANDLER.getValue());
            XxlJobInfo xxlJobInfo = setXxlJobInfoParam(trafficDate, getDruidExecutorParam(maxDays));
            BaseResult resultUpdateReport = executeManageJob("update", xxlJobInfo);
            BaseResult resultTriggerReport = executeManageJob("trigger", xxlJobInfo);

            if (resultUpdateReport.getStatus().equals(HttpStatus.SC_OK) && resultTriggerReport.getStatus().equals(HttpStatus.SC_OK)) {
                baseResult = BaseResultGenerator.success("ok", null);
            } else {
                String info = String.format("启动删除Druid: %s, 更新删除Druid: %s", resultTriggerReport.getStatus(), resultUpdateReport.getStatus());
                baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, info);
            }

        } else {
            baseResult = BaseResultGenerator.failure(HttpStatus.SC_OK, "没有匹配的logType");
        }
        return baseResult;
    }

    private BaseResult deleteAllAndUpdate(int maxDays) {
        BaseResult baseResult;
        if (maxDays > 0) {
            BaseResult resultUpdateTraffic = deleteByMaxDays("Traffic Logs", maxDays);
            BaseResult resultUpdateReport = deleteByMaxDays("Report and Metrics", maxDays);
            if (resultUpdateTraffic.getStatus().equals(HttpStatus.SC_OK) && resultUpdateReport.getStatus().equals(HttpStatus.SC_OK)) {
                baseResult = BaseResultGenerator.success("ok", null);
            } else {
                String info =
                        String.format("更新删除ClickHouse: %s; 启动清库Druid: %s, 更新删除: %s",
                                resultUpdateTraffic.getStatus(), resultUpdateReport.getStatus());
                baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, info);
            }
        } else {
            Map trafficDate = getDataByHandler(JobHandlerEnum.DELETE_ALL_TRAFFIC_DATA_JOB_HANDLER.getValue());
            XxlJobInfo xxlJobInfo = setXxlJobInfoParam(trafficDate, getClickHouseExecutorParam(30));
            BaseResult resultUpdateAllTraffic = executeManageJob("update", xxlJobInfo);
            BaseResult resultTriggerAllTraffic = executeManageJob("trigger", xxlJobInfo);

            trafficDate = getDataByHandler(JobHandlerEnum.DELETE_TRAFFIC_DATA_JOB_HANDLER.getValue());
            xxlJobInfo = setXxlJobInfoParam(trafficDate, getClickHouseExecutorParam(30));
            BaseResult resultUpdateTraffic = executeManageJob("update", xxlJobInfo);

            trafficDate = getDataByHandler(JobHandlerEnum.DELETE_ALL_REPORT_AND_METRICS_DATA_JOB_HANDLER.getValue());
            xxlJobInfo = setXxlJobInfoParam(trafficDate, getDruidExecutorParam(365));
            BaseResult resultUpdateAllReport = executeManageJob("update", xxlJobInfo);
            BaseResult resultTriggerAllReport = executeManageJob("trigger", xxlJobInfo);

            trafficDate = getDataByHandler(JobHandlerEnum.DELETE_REPORT_AND_METRICS_DATA_JOB_HANDLER.getValue());
            xxlJobInfo = setXxlJobInfoParam(trafficDate, getDruidExecutorParam(365));
            BaseResult resultUpdateReport = executeManageJob("update", xxlJobInfo);

            if (resultTriggerAllTraffic.getStatus().equals(HttpStatus.SC_OK) && resultUpdateAllTraffic.getStatus().equals(HttpStatus.SC_OK) && resultUpdateTraffic.getStatus().equals(HttpStatus.SC_OK)
                    && resultTriggerAllReport.getStatus().equals(HttpStatus.SC_OK) && resultUpdateAllReport.getStatus().equals(HttpStatus.SC_OK) && resultUpdateReport.getStatus().equals(HttpStatus.SC_OK)) {
                baseResult = BaseResultGenerator.success("ok", null);
            } else {
                String info =
                        String.format("启动清库Druid: %s, 更新清库Druid: %s, 更新删除Druid: %s; 启动清库clickHouse: %s, 更新清库ClickHouse: %s, 更新删除ClickHouse: %s",
                                resultTriggerAllReport.getStatus(), resultUpdateAllReport.getStatus(), resultUpdateReport.getStatus(),
                                resultTriggerAllTraffic.getStatus(), resultUpdateAllTraffic.getStatus(), resultUpdateTraffic.getStatus());
                baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, info);
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
        if (StringUtil.isEmpty(resultMap)) {
            baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, "服务繁忙，请联系调度平台方！");
        } else {
            if (resultMap.get("status").equals(String.valueOf(HttpStatus.SC_OK))) {
                baseResult = BaseResultGenerator.success("ok", null);
            } else if (resultMap.get("status").equals(String.valueOf(HttpStatus.SC_NOT_FOUND))) {
                baseResult = BaseResultGenerator.failure(HttpStatus.SC_NOT_FOUND, resultMap.get("result"));
            } else {
                baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, resultMap.get("result"));
            }
        }
        return baseResult;
    }


    /**
     * 获取调度平台Cookie
     *
     * @return
     */
    public void setCookie() {
        StringBuilder url = new StringBuilder(jobAdminHttpSource.getUrl());
        Map headers = Maps.newHashMap();
        headers.put("Content-Type", "application/json");
        String urlParamsByMap = getUrlParamsByMap(getObjectToMap(jobAdminHttpSource));
        Map httpPostResponseHeads = httpClientService.getHttpPostResponseHeads(url + "/login?" + urlParamsByMap, headers);
        String cookie = String.valueOf(httpPostResponseHeads.get("SET-COOKIE"));
        headers.put("Cookie", cookie);
        headers.put("Content-Type", "application/json");
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
        log.info("请求调度任务pageList接口" + url);
        if (resultPageList.get("status").equals(String.valueOf(HttpStatus.SC_OK))) {
            Map<String, Object> maps = (Map<String, Object>) JsonMapper.fromJsonString(resultPageList.get("result"), Map.class);
            List<Map> data = (List) maps.get("data");
            if (data.size() >= 1) {
                return data.get(0);
            }
        }
        return new HashMap();
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

    /**
     * 获取druid 执行参数
     *
     * @param maxDays
     * @return
     */
    private ExecutorParam getDruidExecutorParam(int maxDays) {
        ExecutorParam executorParam = new ExecutorParam();
        executorParam.setMaxdays(maxDays);
        return executorParam;
    }

    /**
     * 获取clickHouse 执行参数
     *
     * @param maxDays
     * @return
     */
    private ExecutorParam getClickHouseExecutorParam(int maxDays) {
        ExecutorParam executorParam = new ExecutorParam();
        executorParam.setMaxdays(maxDays);
        return executorParam;
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
}
