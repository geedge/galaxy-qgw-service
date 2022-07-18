package com.mesalab.services.service.impl;


import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.collect.Maps;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.enums.QueryOptionEnum;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.service.ApiService;
import com.mesalab.services.common.property.SqlPropertySourceFactory;
import com.mesalab.services.configuration.JobConfig;
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
import java.util.stream.Collectors;

import static com.mesalab.services.service.impl.JobServiceImp.executeUpdate;

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
    private static final int TOP_MAX = 28800;
    @Autowired
    Environment env;
    @Autowired
    ApiService apiService;

    @Async("taskExecutor")
    public Future<Boolean> executeFieldDiscovery(String id, String logType, String filter, String field, long currentCount) {
        BaseResult currentTop = queryTop(logType, filter, field);
        if (!currentTop.isSuccess()) {
            executeUpdate(String.format(Objects.requireNonNull(env.getProperty("JOB_UPDATE_FIELD_VALUE")), JobConfig.DETAIL, JobConfig.IS_ERROR, id, true));
            log.error("subTask query currentTopK Error: ", currentTop.getMessage());
            return new AsyncResult<>(false);
        }
        List<Map<String, Object>> data = (List<Map<String, Object>>) currentTop.getData();

        BaseResult lastTop = apiService.executeQuery(ApiParam.builder()
                .option(QueryOptionEnum.REAL_TIME.getValue())
                .query(String.format(Objects.requireNonNull(env.getProperty("FIELD_RESULT")), JobConfig.FIELD_DISCOVERY, field, id))
                .build());
        if (!lastTop.isSuccess()) {
            executeUpdate(String.format(Objects.requireNonNull(env.getProperty("JOB_UPDATE_FIELD_VALUE")), JobConfig.DETAIL, JobConfig.IS_ERROR, id, true));
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
                    data.addAll(topk);
                }
            }
        }
        List<Object> topResult = mergeSort(data);
        updateResult(id, field, currentCount, lastCount, topResult);
        return new AsyncResult<>(true);
    }

    private BaseResult queryTop(String logType, String filter, String field) {
        String sql = String.format(Objects.requireNonNull(env.getProperty("JOB_TOPK")),
                field, logType, StrUtil.isBlankIfStr(filter) ? "" : "WHERE ".concat(filter));
        BaseResult currentTop = apiService.executeQuery(ApiParam.builder().option(QueryOptionEnum.REAL_TIME.getValue()).
                query(sql).build());
        return currentTop;
    }

    private void updateResult(String id, String field, long currentCount, Object lastCount, List<Object> topList) {
        Map<String, Object> map = Maps.newHashMap();
        map.put("topk", topList.size() > TOP_MAX ? topList.subList(0, TOP_MAX) : topList);
        map.put("distinct_count", Math.min(topList.size(), 100));
        map.put("count", currentCount + Long.parseLong(lastCount.toString()));
        String result = JSONUtil.quote(JSONUtil.toJsonStr(map), false).replace("'", "\\'");
        executeUpdate(String.format(Objects.requireNonNull(env.getProperty("JOB_UPDATE_FIELD_VALUE_SINGLE_QUOTATION")), JobConfig.FIELD_DISCOVERY, field, id, result));
    }

    private List<Object> mergeSort(List<Map<String, Object>> data) {
        List<Object> collect = data.stream()
                .collect(Collectors.groupingBy(group -> String.valueOf(group.get("value"))))
                .entrySet().stream().map(map -> {
                    int count = map.getValue().stream().mapToInt(o -> Integer.parseInt(o.get("count").toString())).sum();
                    HashMap<String, Object> item = Maps.newHashMap();
                    item.put("value", map.getKey());
                    item.put("count", count);
                    return item;
                }).collect(Collectors.toList());
        List<Object> result = collect.stream().sorted(Comparator.comparingInt(x -> Integer.parseInt(String.valueOf(((Map) x).get("count")))).reversed()).collect(Collectors.toList());
        return result;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.env = environment;
    }
}
