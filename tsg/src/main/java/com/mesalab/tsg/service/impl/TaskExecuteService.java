package com.mesalab.tsg.service.impl;


import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson.JSONArray;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayway.jsonpath.JsonPath;
import com.mesalab.qgw.config.JobConfig;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.DataTypeMapping;
import com.mesalab.common.enums.QueryOptionEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.EngineConfigSource;
import com.mesalab.qgw.service.ApiService;
import com.mesalab.qgw.common.property.SqlPropertySourceFactory;
import com.zdjizhi.utils.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.Future;

/**
 * TODO
 *
 * @Classname TaskService
 * @Date 2021/11/25 10:11 下午
 * @Author wWei
 */
@Service("taskExecuteService")
@PropertySource(value = "classpath:job-sql-template.sql", factory = SqlPropertySourceFactory.class)
public class TaskExecuteService implements EnvironmentAware {
    private static final Log log = LogFactory.get();
    @Autowired
    Environment env;
    @Autowired
    ApiService apiService;
    @Autowired
    EngineConfigSource engineConfigSource;

    @Async("taskExecutor")
    public Future<Boolean> executeFieldDiscovery(String id, String logType, String filter, String field, long currentCount) {
        BaseResult currentTop = queryTop(logType, filter, field);
        if (!currentTop.isSuccess()) {
            JobServiceImp.executeUpdate(String.format(Objects.requireNonNull(env.getProperty("JOB_UPDATE_FIELD_VALUE")), JobConfig.DETAIL, JobConfig.IS_ERROR, id, true));
            log.error("subTask query currentTopK Error: ", currentTop.getMessage());
            return new AsyncResult<>(false);
        }
        List<Map<String, Object>> data = (List<Map<String, Object>>) currentTop.getData();

        BaseResult lastTop = apiService.executeQuery(ApiParam.builder()
                .option(QueryOptionEnum.REAL_TIME.getValue())
                .query(String.format(Objects.requireNonNull(env.getProperty("FIELD_RESULT")), JobConfig.FIELD_DISCOVERY, field, id))
                .build());
        if (!lastTop.isSuccess()) {
            JobServiceImp.executeUpdate(String.format(Objects.requireNonNull(env.getProperty("JOB_UPDATE_FIELD_VALUE")), JobConfig.DETAIL, JobConfig.IS_ERROR, id, true));
            log.error("subTask query lastTopK Error: ", lastTop.getMessage());
            return new AsyncResult<>(false);
        }
        Object lastCount = 0;
        if (StringUtil.isNotEmpty(lastTop.getData())) {
            List<Map> map = (List<Map>) lastTop.getData();
            if (StringUtil.isNotEmpty(map.get(0).get("value"))) {
                Map item = JSONUtil.toBean(String.valueOf(map.get(0).get("value")), Map.class);
                if (StringUtil.isNotEmpty(item)) {
                    List<Map<String, Object>> topk = (List<Map<String, Object>>) item.get("topk");
                    lastCount = item.get("count");
                    Map<String, String> metrics = Maps.newHashMap();
                    metrics.put("count", "sum");
                    data = mergeData((List<Map<String, Object>>) JSONArray.parse(JSONUtil.toJsonStr(data)), topk, Lists.newArrayList("value"), metrics);
                }
            }
        }
        data = sortDataAndSetMaxSize(data, "count", DataTypeMapping.LONG, false);
        updateResult(id, field, currentCount, lastCount, data);
        return new AsyncResult<>(true);
    }

    @Async("taskExecutor")
    public Future<Boolean> executeStatistics(String id, String sql) {
        try {
            BaseResult lastResult = apiService.executeQuery(ApiParam.builder().query(sql).build());
            BaseResult preResult = apiService.executeQuery(ApiParam.builder().query(String.format(Objects.requireNonNull(env.getProperty("JOB_RESULT")), id)).build());
            List<Map<String, Object>> lastData = (List<Map<String, Object>>) lastResult.getData();
            List<Map<String, Object>> lastMeta = (List<Map<String, Object>>) lastResult.getMeta();
            Map preDataFirst = ((List<Map>) preResult.getData()).get(0);
            Map<String, Object> property = JsonPath.read(JSONUtil.toJsonStr(preDataFirst.get(JobConfig.JOB_PROPERTY)), "$");
            Map<String, Object> result = Maps.newHashMap();
            if (JobConfig.JOB_PROPERTY_V_OPTION_TIMECHART.equals(property.get(JobConfig.JOB_PROPERTY_K_OPTION))) {
                timechartData(lastData, lastMeta, preDataFirst, result);
            } else if (JobConfig.JOB_PROPERTY_V_OPTION_TOP.equals(property.get(JobConfig.JOB_PROPERTY_K_OPTION))
                    || JobConfig.JOB_PROPERTY_V_OPTION_RARE.equals(property.get(JobConfig.JOB_PROPERTY_K_OPTION))) {
                topOrRareData(lastData, lastMeta, preDataFirst, result);
            } else {
                result.put("data", lastData);
                result.put("meta", lastMeta);
            }
            JobServiceImp.executeUpdate(String.format(Objects.requireNonNull(env.getProperty("JOB_UPDATE_FIELD_VALUE_SINGLE_QUOTATION")), JobConfig.STATISTICS, JobConfig.STATISTICS_RESULT, id, JSONUtil.quote(JSONUtil.toJsonStr(result), false).replace("'", "\\'")));
        } catch (RuntimeException ex) {
            log.error("task execute error, message is: {}", ex.getMessage());
            JobServiceImp.executeUpdate(String.format(Objects.requireNonNull(env.getProperty("JOB_UPDATE_FIELD_VALUE")), JobConfig.DETAIL, JobConfig.IS_ERROR, id, true));
        }
        return new AsyncResult<>(true);
    }

    private void topOrRareData(List<Map<String, Object>> lastData, List<Map<String, Object>> lastMeta, Map preDataFirst, Map<String, Object> valueMap) {
        List<Map<String, Object>> preData;
        List<String> dimensions = JsonPath.read(JSONUtil.toJsonStr(preDataFirst.get(JobConfig.JOB_PROPERTY)), "$.sub_option.dimension");
        Map<String, String> metrics = JsonPath.read(JSONUtil.toJsonStr(preDataFirst.get(JobConfig.JOB_PROPERTY)), "$.sub_option.metric");
        Map<String, Boolean> isAsc = JsonPath.read(JSONUtil.toJsonStr(preDataFirst.get(JobConfig.JOB_PROPERTY)), "$.sub_option.isAsc");
        String sortKey = isAsc.keySet().stream().findFirst().get();
        if (StringUtil.isNotEmpty(preDataFirst.get(JobConfig.STATISTICS_RESULT))) {
            Map<String, Object> item = JSONUtil.toBean(preDataFirst.get(JobConfig.STATISTICS_RESULT).toString(), Map.class);
            preData = (List<Map<String, Object>>) item.get("data");
            lastMeta = (List<Map<String, Object>>) item.get("meta");
            lastData = mergeData(lastData, preData, dimensions, metrics);
            Map<String, String> meta = Maps.newHashMap();
            lastMeta.forEach(o -> meta.put(String.valueOf(o.get("name")), String.valueOf(o.get("type"))));
            lastData = sortDataAndSetMaxSize(lastData, sortKey, meta.get(sortKey), isAsc.values().stream().findFirst().get());
        }
        valueMap.put("data", lastData);
        valueMap.put("meta", lastMeta);
    }

    private void timechartData(List<Map<String, Object>> lastData, List<Map<String, Object>> lastMeta, Map preDataFirst, Map<String, Object> valueMap) {
        List<Map<String, Object>> preData;
        if (StringUtil.isNotEmpty(preDataFirst.get(JobConfig.STATISTICS_RESULT))) {
            Map<String, Object> item = JSONUtil.toBean(preDataFirst.get(JobConfig.STATISTICS_RESULT).toString(), Map.class);
            preData = (List<Map<String, Object>>) item.get("data");
            lastMeta = (List<Map<String, Object>>) item.get("meta");
            Map<String, Boolean> isAsc = JsonPath.read(JSONUtil.toJsonStr(preDataFirst.get(JobConfig.JOB_PROPERTY)), "$.sub_option.isAsc");
            Map<String, String> meta = Maps.newHashMap();
            lastMeta.forEach(o -> meta.put(String.valueOf(o.get("name")), String.valueOf(o.get("type"))));
            lastData = appendData(lastData, preData, isAsc.values().stream().findFirst().get());
        }
        valueMap.put("meta", lastMeta);
        valueMap.put("data", lastData);
    }

    private List<Map<String, Object>> sortDataAndSetMaxSize(List<Map<String, Object>> data, String sortElement, String sortType, boolean isAsc) {
        if (DataTypeMapping.INT.equalsIgnoreCase(sortType) || DataTypeMapping.LONG.equalsIgnoreCase(sortType)) {
            data.sort(Comparator.comparing(o -> Long.valueOf(StringUtil.isEmpty(o.get(sortElement)) ? Long.MIN_VALUE + "" : o.get(sortElement).toString())));
        } else if (DataTypeMapping.FLOAT.equalsIgnoreCase(sortType) || DataTypeMapping.DOUBLE.equalsIgnoreCase(sortType)) {
            data.sort(Comparator.comparing(o -> Double.valueOf(StringUtil.isEmpty(o.get(sortElement)) ? Long.MIN_VALUE + "" : o.get(sortElement).toString())));
        } else {
            data.sort(Comparator.comparing(o -> String.valueOf(o.get(sortElement))));
        }
        if (!isAsc) {
            Collections.reverse(data);
        }
        return data.size() > engineConfigSource.getMaxCacheNum() ? data.subList(0, engineConfigSource.getMaxCacheNum()) : data;
    }

    private List<Map<String, Object>> mergeData(List<Map<String, Object>> data1, List<Map<String, Object>> date2, List<String> dimensions, Map<String, String> metrics) {
        Collection<Map<String, Object>> data = CollectionUtil.addAll(data1, date2);
        Map<String, Map<String, Object>> result = Maps.newHashMap();
        for (Map<String, Object> datum : data) {
            StringBuilder key = new StringBuilder();
            dimensions.forEach(k -> key.append(datum.get(k)));
            if (!result.containsKey(key.toString())) {
                result.put(key.toString(), datum);
                continue;
            }
            Map<String, Object> item = result.get(key.toString());
            for (String k : metrics.keySet()) {
                String action = metrics.get(k);
                Object o1 = item.get(k);
                Object o2 = datum.get(k);
                if (StringUtil.isEmpty(o1) || StringUtil.isEmpty(o2)) {
                    item.put(k, (StringUtil.isEmpty(o1) && StringUtil.isEmpty(o2)) ? null : StringUtil.isEmpty(o1) ? o2 : o1);
                    continue;
                }
                Number number1 = NumberUtil.parseNumber(o1.toString());
                Number number2 = NumberUtil.parseNumber(o2.toString());
                if (action.equals("sum")) {
                    item.put(k, NumberUtil.add(number1, number2));
                    continue;
                }
                if (NumberUtil.isDouble(o1.toString()) || NumberUtil.isDouble(o2.toString())) {
                    double v1 = Double.parseDouble(number1.toString());
                    double v2 = Double.parseDouble(number2.toString());
                    switch (action) {
                        case "max":
                            item.put(k, NumberUtil.max(v1, v2));
                            break;
                        case "min":
                            item.put(k, NumberUtil.min(v1, v2));
                            break;
                        case "avg":
                            item.put(k, NumberUtil.div(NumberUtil.add(v1, v2), 2));
                            break;
                    }
                } else if (NumberUtil.isLong(o1.toString()) || NumberUtil.isLong(o2.toString())) {
                    long v1 = Long.parseLong(number1.toString());
                    long v2 = Long.parseLong(number2.toString());
                    switch (action) {
                        case "max":
                            item.put(k, NumberUtil.max(v1, v2));
                            break;
                        case "min":
                            item.put(k, NumberUtil.min(v1, v2));
                            break;
                        case "avg":
                            item.put(k, NumberUtil.div(NumberUtil.add(v1, v2), 2));
                            break;
                    }
                } else {
                    log.error("task merge data error, data is: {}, {}", data1, date2);
                    throw new BusinessException("task merge data error.");
                }
            }
        }
        return new ArrayList<>(result.values());
    }

    private List<Map<String, Object>> appendData(List<Map<String, Object>> lastData, List<Map<String, Object>> preData, Boolean isAsc) {
        if (isAsc) {
            lastData.addAll(preData);
        } else {
            preData.addAll(lastData);
        }
        return lastData.size() > preData.size() ? lastData : preData;
    }

    private BaseResult queryTop(String logType, String filter, String field) {
        String sql = String.format(Objects.requireNonNull(env.getProperty("JOB_TOPK")),
                field, logType, StrUtil.isBlankIfStr(filter) ? "" : "WHERE ".concat(filter));
        BaseResult currentTop = apiService.executeQuery(ApiParam.builder().option(QueryOptionEnum.REAL_TIME.getValue()).
                query(sql).build());
        return currentTop;
    }

    private void updateResult(String id, String field, long currentCount, Object lastCount, List<Map<String, Object>> topList) {
        Map<String, Object> map = Maps.newHashMap();
        map.put("topk", topList);
        map.put("distinct_count", Math.min(topList.size(), 100));
        map.put("count", currentCount + Long.parseLong(lastCount.toString()));
        String result = JSONUtil.quote(JSONUtil.toJsonStr(map), false).replace("'", "\\'");
        JobServiceImp.executeUpdate(String.format(Objects.requireNonNull(env.getProperty("JOB_UPDATE_FIELD_VALUE_SINGLE_QUOTATION")), JobConfig.FIELD_DISCOVERY, field, id, result));
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.env = environment;
    }
}
