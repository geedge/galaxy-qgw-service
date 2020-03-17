package com.mesalab.qgw.service.impl;

import com.google.common.collect.Maps;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.BaseResultEnum;
import com.mesalab.common.enums.JobHandlerEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.common.utils.SpringContextUtil;
import com.mesalab.qgw.model.ExecutorParam;
import com.mesalab.qgw.model.XxlJobInfo;
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


    ClickHouseHttpSource clickHouseHttpSource = (ClickHouseHttpSource) SpringContextUtil.getBean("clickHouseHttpSource");
    JobAdminHttpSource jobAdminHttpSource = (JobAdminHttpSource) SpringContextUtil.getBean("jobAdminHttpSource");
    private DruidIoHttpSource druidIoHttpSource = (DruidIoHttpSource) SpringContextUtil.getBean("druidIoHttpSource");
    private HttpClientService httpClientService = (HttpClientService) SpringContextUtil.getBean("httpClientService");

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
    public BaseResult deleteStorage(String logType, int maxDays) {
        if (0 == maxDays) {
            return executeDeleteAllLogs(logType);
        } else {
            return executeDeleteByMaxDays(logType, maxDays);
        }
    }

    public String getCookie() {
        StringBuilder url = new StringBuilder(jobAdminHttpSource.getUrl());
        Map headers = Maps.newHashMap();
        headers.put("Content-Type", "application/json");
        String urlParamsByMap = getUrlParamsByMap(getObjectToMap(jobAdminHttpSource));
        Map httpPostResponseHeads = httpClientService.getHttpPostResponseHeads(url + "/login?" + urlParamsByMap, headers);
        return String.valueOf(httpPostResponseHeads.get("SET-COOKIE"));
    }

    /**
     * 删除指定最大天数的日志
     *
     * @param logType
     * @param maxDays
     * @return
     */
    private BaseResult executeDeleteByMaxDays(String logType, int maxDays) {
        BaseResult baseResult = null;
        StringBuilder urlBuilder = new StringBuilder(jobAdminHttpSource.getUrl());
        String url = urlBuilder.append("/jobinfo/update?").toString();
        if ("Traffic Logs".equalsIgnoreCase(logType)) {
            Map data = getDataByHandler(JobHandlerEnum.DELETE_TRAFFIC_DATA_JOB_HANDLER.getValue());
            ExecutorParam executorParam = getClickHouseExecutorParam(maxDays);
            String updateParam = setJobInfoParam(data, executorParam);
            baseResult = httpGetUpdateJobInfo(url, updateParam);
        } else if ("Report and Metrics".equalsIgnoreCase(logType)) {
            Map data = getDataByHandler(JobHandlerEnum.DELETE_REPORT_AND_METRICS_DATA_JOB_HANDLER.getValue());
            ExecutorParam executorParam = getDruidExecutorParam(maxDays);
            String updateParam = setJobInfoParam(data, executorParam);
            baseResult = httpGetUpdateJobInfo(url, updateParam);
        } else if ("ALL".equalsIgnoreCase(logType)) {
            Map data = getDataByHandler(JobHandlerEnum.DELETE_TRAFFIC_DATA_JOB_HANDLER.getValue());
            ExecutorParam executorParam = getClickHouseExecutorParam(maxDays);
            String updateParam = setJobInfoParam(data, executorParam);
            BaseResult baseResult1 = httpGetUpdateJobInfo(url, updateParam);

            data = getDataByHandler(JobHandlerEnum.DELETE_REPORT_AND_METRICS_DATA_JOB_HANDLER.getValue());
            executorParam = getDruidExecutorParam(maxDays);
            updateParam = setJobInfoParam(data, executorParam);
            BaseResult baseResult2 = httpGetUpdateJobInfo(url, updateParam);

            if (baseResult1.getStatus().equals(HttpStatus.SC_OK) && baseResult2.getStatus().equals(HttpStatus.SC_OK)) {
                baseResult = BaseResultGenerator.success("ok", null);
            } else {
                baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, baseResult1.getMessage() + "\n" + baseResult2.getMessage());
            }
        } else {
            baseResult = BaseResultGenerator.failure(HttpStatus.SC_OK, "没有匹配的logType");
        }
        return baseResult;
    }

    /**
     * 删除所有日志
     *
     * @param logType
     * @return
     */
    private BaseResult executeDeleteAllLogs(String logType) {
        BaseResult baseResult = null;
        StringBuilder urlBuilder = new StringBuilder(jobAdminHttpSource.getUrl());
        String url = urlBuilder.append("/jobinfo/trigger?").toString();
        if ("Traffic Logs".equalsIgnoreCase(logType)) {
            Map data = getDataByHandler(JobHandlerEnum.DELETE_ALL_TRAFFIC_DATA_JOB_HANDLER.getValue());
            ExecutorParam executorParam = getClickHouseExecutorParam(0);
            String updateParam = setJobInfoParam(data, executorParam);
            baseResult = httpGetUpdateJobInfo(url, updateParam);
        } else if ("Report and Metrics".equalsIgnoreCase(logType)) {
            Map data = getDataByHandler(JobHandlerEnum.DELETE_ALL_REPORT_AND_METRICS_DATA_JOB_HANDLER.getValue());
            Map temp = getDataByHandler(JobHandlerEnum.DELETE_REPORT_AND_METRICS_DATA_JOB_HANDLER.getValue());
            Map<String, Object> map = (Map<String, Object>) JsonMapper.fromJsonString(temp.get("executorParam").toString(), Map.class);
            ExecutorParam executorParam = getDruidExecutorParam(Integer.parseInt(map.get("maxdays").toString()));
            String updateParam = setJobInfoParam(data, executorParam);
            baseResult = httpGetUpdateJobInfo(url, updateParam);
        } else if ("ALL".equalsIgnoreCase(logType)) {
            Map data = getDataByHandler(JobHandlerEnum.DELETE_ALL_TRAFFIC_DATA_JOB_HANDLER.getValue());
            ExecutorParam executorParam = getClickHouseExecutorParam(0);
            String updateParam = setJobInfoParam(data, executorParam);
            BaseResult baseResult1 = httpGetUpdateJobInfo(url, updateParam);

            data = getDataByHandler(JobHandlerEnum.DELETE_ALL_REPORT_AND_METRICS_DATA_JOB_HANDLER.getValue());
            Map temp = getDataByHandler(JobHandlerEnum.DELETE_REPORT_AND_METRICS_DATA_JOB_HANDLER.getValue());
            Map<String, Object> map = (Map<String, Object>) JsonMapper.fromJsonString(temp.get("executorParam").toString(), Map.class);
            executorParam = getDruidExecutorParam(Integer.parseInt(map.get("maxdays").toString()));
            updateParam = setJobInfoParam(data, executorParam);
            BaseResult baseResult2 = httpGetUpdateJobInfo(url, updateParam);

            if (baseResult1.getStatus().equals(HttpStatus.SC_OK) && baseResult2.getStatus().equals(HttpStatus.SC_OK)) {
                baseResult = BaseResultGenerator.success("ok", null);
            } else {
                baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, baseResult1.getMessage() + "\n" + baseResult2.getMessage());
            }

        }
        return baseResult;
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
        String cookie = getCookie();
        Map<String, String> headers = Maps.newHashMap();
        headers.put("Cookie", cookie);
        headers.put("Content-Type", "application/json");
        Map<String, String> resultPageList = httpClientService.httpGet(url.toString(), headers);
        if (resultPageList.get("status").equals(String.valueOf(HttpStatus.SC_OK))) {
            Map<String, Object> maps = (Map<String, Object>) JsonMapper.fromJsonString(resultPageList.get("result"), Map.class);
            List<Map> data = (List) maps.get("data");
            if (data.size() == 1) {
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
    private String setJobInfoParam(Map data, ExecutorParam executorParam) {
        XxlJobInfo xxlJobInfo = mapToBean(data, XxlJobInfo.class);
        xxlJobInfo.setExecutorParam(JsonMapper.toJsonString(executorParam));
        return getUrlParamsByMap(getObjectToMap(xxlJobInfo));
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
        executorParam.setServer(druidIoHttpSource.getUrl().substring(0, druidIoHttpSource.getUrl().indexOf("/")));
        executorParam.setDatasource(druidIoHttpSource.getDbname());
        executorParam.setTablename("top_external_host_log_aggr");
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
        executorParam.setServer(clickHouseHttpSource.getUrl());
        executorParam.setDatasource(clickHouseHttpSource.getDbname());
        executorParam.setUsername(clickHouseHttpSource.getUsername());
        executorParam.setPassword(clickHouseHttpSource.getPassword());
        executorParam.setTablename("connection_record_log_local_ttl2");
        return executorParam;
    }


    /**
     * 更新jobInfo
     *
     * @param url
     * @param params
     * @return
     */
    private BaseResult httpGetUpdateJobInfo(String url, String params) {
        BaseResult baseResult;
        String cookie = getCookie();
        Map<String, String> headers = Maps.newHashMap();
        headers.put("Cookie", cookie);
        headers.put("Content-Type", "application/json");
        Map<String, String> resultMap = httpClientService.httpGet(url + params, headers);
        log.info(url + params);
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
        System.out.println(clazz);
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
