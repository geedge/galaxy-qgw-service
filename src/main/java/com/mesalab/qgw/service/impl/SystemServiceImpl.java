package com.mesalab.qgw.service.impl;

import com.google.common.collect.Maps;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.BaseResultEnum;
import com.mesalab.common.enums.JobHandlerEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.common.utils.SpringContextUtil;
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
import org.springframework.cglib.beans.BeanMap;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.*;

@Slf4j
@Service
public class SystemServiceImpl implements SystemService {




    private IAPIService apiService = (IAPIService) SpringContextUtil.getBean("apiService");
    private JobAdminHttpSource jobAdminHttpSource = (JobAdminHttpSource) SpringContextUtil.getBean("jobAdminHttpSource");
    private HttpClientService httpClientService = (HttpClientService) SpringContextUtil.getBean("httpClientService");

    private Map<String, String> headers = Maps.newHashMap();

    private static String TRAFFIC_LOGS = "Traffic Logs";
    private static String REPORT_AND_METRICS = "Report and Metrics";
    private static String FILES = "Files";
    private static String ALL = "ALL";

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
        BaseResult baseResult = null;
        List<BaseResult> listResult = new ArrayList<>();
        String messages = StringUtil.EMPTY;
        setCookie();
        for (StorageDeletionInfo info : list) {
            BaseResult result = executeDeleteStorageJob(info.getLogType(), info.getMaxDays());
            listResult.add(result);
        }
        for (BaseResult result : listResult) {
            if (!result.getStatus().equals(HttpStatus.SC_OK)) {
                messages += result.getMessage() + "; ";
                baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, messages + "其它未提示的则为成功");
            }
        }
        if (baseResult == null) {
            baseResult = BaseResultGenerator.success("ok", null);
        }
        return baseResult;
    }

    private BaseResult executeDeleteStorageJob(String logType, int maxDays) {
        BaseResult baseResult;
        if (maxDays == 0) {
            baseResult = deleteAll(logType);
        } else {
            baseResult = deletePart(logType, maxDays);
        }
        return baseResult;
    }

    @Override
    public BaseResult getDeleteStorageStatus(String logType) {
        setCookie();
        List list = new ArrayList<>();
        if (StringUtil.isBlank(logType)) {

            StorageDeletionInfo trafficInfo = getStorageDeletionInfoByHandler(TRAFFIC_LOGS, JobHandlerEnum.DELETE_TRAFFIC_DATA_JOB_HANDLER.getValue());
            list.add(trafficInfo);

            StorageDeletionInfo reportInfo = getStorageDeletionInfoByHandler(REPORT_AND_METRICS, JobHandlerEnum.DELETE_REPORT_AND_METRICS_DATA_JOB_HANDLER.getValue());
            list.add(reportInfo);

            StorageDeletionInfo storageDeletionInfo = new StorageDeletionInfo();
            storageDeletionInfo.setLogType(FILES);//伪代码
            storageDeletionInfo.setMaxDays(365);
            list.add(storageDeletionInfo);
        } else if (TRAFFIC_LOGS.equalsIgnoreCase(logType)) {
            StorageDeletionInfo trafficInfo = getStorageDeletionInfoByHandler(logType, JobHandlerEnum.DELETE_TRAFFIC_DATA_JOB_HANDLER.getValue());
            list.add(trafficInfo);
        } else if (REPORT_AND_METRICS.equalsIgnoreCase(logType)) {
            StorageDeletionInfo reportInfo = getStorageDeletionInfoByHandler(logType, JobHandlerEnum.DELETE_REPORT_AND_METRICS_DATA_JOB_HANDLER.getValue());
            list.add(reportInfo);
        } else if (FILES.equalsIgnoreCase(logType)) {
            StorageDeletionInfo reportInfo = getStorageDeletionInfoByHandler(logType, JobHandlerEnum.DELETE_FILES_JOB_HANDLER.getValue());
            list.add(reportInfo);
        } else {
            return BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, "没有匹配的日志类型: " + logType);
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

    /**
     * 删除部分日志
     * @param logType
     * @param maxDays
     * @return
     */
    private BaseResult deletePart(String logType, int maxDays) {
        BaseResult baseResult;
        if (TRAFFIC_LOGS.equalsIgnoreCase(logType)) {
            Map trafficDate = getDataByHandler(JobHandlerEnum.DELETE_TRAFFIC_DATA_JOB_HANDLER.getValue());
            XxlJobInfo xxlJobInfo = setXxlJobInfoParam(trafficDate, getExecutorParam(maxDays));
            baseResult = executeAndUpdate(xxlJobInfo);
        } else if (REPORT_AND_METRICS.equalsIgnoreCase(logType)) {
            Map trafficDate = getDataByHandler(JobHandlerEnum.DELETE_REPORT_AND_METRICS_DATA_JOB_HANDLER.getValue());
            XxlJobInfo xxlJobInfo = setXxlJobInfoParam(trafficDate, getExecutorParam(maxDays));
            baseResult = executeAndUpdate(xxlJobInfo);
        } else if (FILES.equalsIgnoreCase(logType)) {
            baseResult = BaseResultGenerator.success("ok", null);//伪代码
        } else if(ALL.equalsIgnoreCase(logType)){
            BaseResult resultTraffic = deletePart(TRAFFIC_LOGS, maxDays);
            BaseResult resultReport = deletePart(REPORT_AND_METRICS, maxDays);
            BaseResult resultFiles = deletePart(FILES, maxDays);
            if (resultTraffic.getStatus().equals(HttpStatus.SC_OK)
                    && resultReport.getStatus().equals(HttpStatus.SC_OK)
                    && resultFiles.getStatus().equals(HttpStatus.SC_OK)) {
                baseResult = BaseResultGenerator.success("所有日志类型数据删除成功", null);
            } else {
                baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, resultTraffic.getMessage() +
                        ";" + resultReport.getMessage() +
                        ";" + resultFiles.getMessage());
            }
        } else {
            baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, "没有匹配的日志类型: " + logType);
        }
        return baseResult;
    }

    /**
     * 清空日志
     *
     * @param logType
     * @return
     */
    private BaseResult deleteAll(String logType) {
        BaseResult baseResult;
        if (TRAFFIC_LOGS.equalsIgnoreCase(logType)) {
            ExecutorParam executorParam = getExecutorParam(30);
            baseResult = getResultOfDeleteAll(JobHandlerEnum.DELETE_ALL_TRAFFIC_DATA_JOB_HANDLER.getValue(),
                    JobHandlerEnum.DELETE_TRAFFIC_DATA_JOB_HANDLER.getValue(),
                    executorParam);
        } else if (REPORT_AND_METRICS.equalsIgnoreCase(logType)) {
            ExecutorParam executorParam = getExecutorParam(365);
            baseResult = getResultOfDeleteAll(JobHandlerEnum.DELETE_ALL_REPORT_AND_METRICS_DATA_JOB_HANDLER.getValue(),
                    JobHandlerEnum.DELETE_REPORT_AND_METRICS_DATA_JOB_HANDLER.getValue(),
                    executorParam);
        } else if (FILES.equalsIgnoreCase(logType)) {
            baseResult = BaseResultGenerator.success("ok", null);//伪代码
        } else if (ALL.equalsIgnoreCase(logType)) {
            BaseResult resultTraffic = deleteAll(TRAFFIC_LOGS);
            BaseResult resultReport = deleteAll(REPORT_AND_METRICS);
            BaseResult resultFiles = deleteAll(FILES);
            if (resultTraffic.getStatus().equals(HttpStatus.SC_OK)
                    && resultReport.getStatus().equals(HttpStatus.SC_OK)
                    && resultFiles.getStatus().equals(HttpStatus.SC_OK)) {
                baseResult = BaseResultGenerator.success("所有日志类型数据清空成功", null);
            } else {
                baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, resultTraffic.getMessage() +
                        ";" + resultReport.getMessage() +
                        ";" + resultFiles.getMessage());
            }
        } else {
            baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, "没有匹配的日志类型: " + logType);
        }
        return baseResult;
    }

    /**
     * 获取清空日志结果
     *
     * @param deleteAllHandler
     * @param deletePartHandler
     * @param executorParam
     * @return
     */
    private BaseResult getResultOfDeleteAll(String deleteAllHandler, String deletePartHandler, ExecutorParam executorParam) {
        BaseResult baseResult;
        Map trafficDate = getDataByHandler(deleteAllHandler);
        XxlJobInfo xxlJobInfo = setXxlJobInfoParam(trafficDate, executorParam);
        BaseResult resultExecuteAndUpdate = executeAndUpdate(xxlJobInfo);
        if (resultExecuteAndUpdate.getStatus().equals(HttpStatus.SC_OK)) {
            Map dataByHandler = getDataByHandler(deletePartHandler);
            XxlJobInfo xxlJobInfoOfUpdate = setXxlJobInfoParam(dataByHandler, executorParam);
            BaseResult resultUpdate = executeManageJob("update", xxlJobInfoOfUpdate);
            if (resultUpdate.getStatus().equals(HttpStatus.SC_OK)) {
                baseResult = BaseResultGenerator.success("ok", null);
            } else {
                baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, deleteAllHandler + ": 执行清库成功, 更新删除部分日志失败。");
            }
        } else {
            baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, resultExecuteAndUpdate.getMessage());
        }
        return baseResult;
    }

    /**
     * 更新任务并触发执行器
     *
     * @param xxlJobInfo
     * @return
     */
    private BaseResult executeAndUpdate(XxlJobInfo xxlJobInfo) {
        BaseResult baseResult;
        BaseResult resultUpdate = executeManageJob("update", xxlJobInfo);
        if (resultUpdate.getStatus().equals(HttpStatus.SC_OK)) {
            BaseResult resultExecute = executeManageJob("trigger", xxlJobInfo);
            if (resultExecute.getStatus().equals(HttpStatus.SC_OK)) {
                baseResult = BaseResultGenerator.success("OK", null);
            } else {
                baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, "更新任务成功, 触发执行器失败: " + xxlJobInfo.getExecutorHandler());
            }
        } else {
            baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, "更新任务失败: " + xxlJobInfo.getExecutorHandler());
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
        log.info("请求调度任务pageList接口" + url);
        if (StringUtil.isNotEmpty(resultPageList) && resultPageList.get("status").equals(String.valueOf(HttpStatus.SC_OK))) {
            Map<String, Object> maps = (Map<String, Object>) JsonMapper.fromJsonString(resultPageList.get("result"), Map.class);
            if (StringUtil.isEmpty(maps)) {
                throw new BusinessException(BaseResultEnum.SERVER_ERROR.getCode(), "获取或封装结果异常: " + resultPageList, null);
            }
            List<Map> data = (List) maps.get("data");
            if (data.size() >= 1) {
                return data.get(0);
            } else {
                throw new BusinessException(BaseResultEnum.FAIL.getCode(), "无匹配executorHandler(" + handlerValue + ")的jobInfo信息", null);
            }
        }
        throw new BusinessException(BaseResultEnum.FAIL.getCode(), "获取jobInfo异常: " + resultPageList, null);
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
     * 获取执行参数
     *
     * @param maxDays
     * @return
     */
    private ExecutorParam getExecutorParam(int maxDays) {
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
