package com.mesalab.services.service.impl;


import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayway.jsonpath.JsonPath;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.DataTypeMapping;
import com.mesalab.common.enums.QueryOption;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.qgw.model.basic.SQLQueryContext;
import com.mesalab.qgw.model.basic.EngineConfigSource;
import com.mesalab.qgw.service.SQLSyncQueryService;
import com.mesalab.qgw.service.DatabaseService;
import com.mesalab.services.common.property.SqlPropertySourceFactory;
import com.mesalab.services.configuration.JobConfig;
import com.geedgenetworks.utils.StringUtil;
import org.apache.avro.LogicalType;
import org.apache.avro.Schema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.Future;

import static com.mesalab.services.service.impl.JobExecuteService.signJobInfoToFailedOnQueryCache;
import static com.mesalab.services.service.impl.JobServiceImpl.executeUpdate;

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
    private Environment env;
    @Autowired
    SQLSyncQueryService sqlSyncQueryService;
    @Autowired
    DatabaseService databaseService;
    @Autowired
    EngineConfigSource engineConfigSource;

    @Async("taskExecutor")
    public Future<List<Map<String, Object>>> executeFieldDiscovery(String id, String dataSource, String field, Object metric, Object fn, String filter) {
        String fnDefault = "count";
        String column = "*";
        String value = "count";
        try {
            if (StringUtil.isNotEmpty(metric)) {
                String measurements = databaseService.getValueByKeyInSchemaDoc(dataSource, "measurements");
                List<Map<String, String>> read = JsonPath.read(measurements, "$.field_discovery_metric['" + metric + "'][?(@.fn == '" + fn + "')]");
                if (!read.isEmpty()) {
                    fnDefault = fn.toString();
                    column = read.get(0).get("column");
                    value = read.get(0).get("value");
                }
            }
            BaseResult currentTop = sqlSyncQueryService.executeQuery(SQLQueryContext.builder()
                    .option(QueryOption.REAL_TIME.getValue())
                    .originalSQL(buildTopSQL(dataSource, filter, field, fnDefault, column, value))
                    .build());
            if (!currentTop.isSuccess()) {
                setJobFailed(id);
                log.error("field_discovery task of Job id is: {}, query currentTopK Error: {}", id, currentTop.getMessage());
                return new AsyncResult<>(Lists.newArrayList());
            }
            List<Map<String, Object>> data = (List<Map<String, Object>>) currentTop.getData();
            return new AsyncResult<>(Lists.newArrayList(data));
        } catch (RuntimeException ex) {
            log.error("field_discovery task of Job id is: {}, execute error, message is: {}", id, ex.getMessage());
            setJobFailed(id);
        }
        return new AsyncResult<>(Lists.newArrayList());
    }

    @Async("taskExecutor")
    public Future<Boolean> executeLongTerm(String id, String sql) {
        try {
            BaseResult lastResult = sqlSyncQueryService.executeQuery(SQLQueryContext.builder().originalSQL(sql).build());
            BaseResult preResult = sqlSyncQueryService.executeQuery(SQLQueryContext.builder().originalSQL(String.format(Objects.requireNonNull(env.getProperty("JOB_RESULT")), id)).build());
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
            executeUpdate(String.format(Objects.requireNonNull(env.getProperty("JOB_UPDATE_FIELD_VALUE_SINGLE_QUOTATION")), JobConfig.LONG_TERM_COLUMN_FAMILY, JobConfig.LONG_TERM_RESULT, id, JSONUtil.quote(JSONUtil.toJsonStr(result), false).replace("'", "\\'")));
        } catch (RuntimeException ex) {
            log.error("Statistics task execute error, message is: {}", ex.getMessage());
            setJobFailedLongTerm(id);
        }
        return new AsyncResult<>(true);
    }

    private void setJobFailed(String id) {
        signJobInfoToFailedOnQueryCache(id);
    }

    @Deprecated
    private void setJobFailedLongTerm(String id) {
        executeUpdate(String.format(Objects.requireNonNull(env.getProperty("JOB_UPDATE_FIELD_VALUE")), JobConfig.DETAIL, JobConfig.IS_FAILED, id, true));
    }


    private void topOrRareData(List<Map<String, Object>> lastData, List<Map<String, Object>> lastMeta, Map preDataFirst, Map<String, Object> valueMap) {
        List<Map<String, Object>> preData;
        List<String> dimensions = JsonPath.read(JSONUtil.toJsonStr(preDataFirst.get(JobConfig.JOB_PROPERTY)), "$.sub_option.dimension");
        Map<String, String> metrics = JsonPath.read(JSONUtil.toJsonStr(preDataFirst.get(JobConfig.JOB_PROPERTY)), "$.sub_option.metric");
        Map<String, Boolean> isAsc = JsonPath.read(JSONUtil.toJsonStr(preDataFirst.get(JobConfig.JOB_PROPERTY)), "$.sub_option.isAsc");
        String sortKey = isAsc.keySet().stream().findFirst().get();
        if (StringUtil.isNotEmpty(preDataFirst.get(JobConfig.LONG_TERM_RESULT))) {
            Map<String, Object> item = JSONUtil.toBean(preDataFirst.get(JobConfig.LONG_TERM_RESULT).toString(), Map.class);
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
        if (StringUtil.isNotEmpty(preDataFirst.get(JobConfig.LONG_TERM_RESULT))) {
            Map<String, Object> item = JSONUtil.toBean(preDataFirst.get(JobConfig.LONG_TERM_RESULT).toString(), Map.class);
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

    private String buildTopSQL(String tableName, String filter, String fieldName, String fn, String column, String value) {
        Schema.Field field = databaseService.getSchemaByName(tableName).getField(fieldName);
        if (StringUtil.isNotEmpty(field)) {
            Schema schema = field.schema();
            String type = schema.getType().getName();
            String logicalType = schema.getProp(LogicalType.LOGICAL_TYPE_PROP);
            if (StrUtil.equalsIgnoreCase(Schema.Type.ARRAY.getName(), type)
                    || StrUtil.equalsIgnoreCase(Schema.Type.ARRAY.getName(), logicalType)) {
                return String.format(Objects.requireNonNull(env.getProperty("JOB_TOPK_ARRAY")),
                        "count".equalsIgnoreCase(fn) ? "SUM" : fn, value, fieldName, fn + "(" + column + ")", tableName, fieldName, StrUtil.isBlankIfStr(filter) ? "" : " AND ".concat(filter), value);
            } else if (StrUtil.equalsIgnoreCase("bit", type)
                    || StrUtil.equalsIgnoreCase("bit", logicalType)) {

                return String.format(Objects.requireNonNull(env.getProperty("JOB_TOPK_BIT")),
                        "count".equalsIgnoreCase(fn) ? "SUM" : fn, value, fieldName, fn + "(" + column + ")", tableName, StrUtil.isBlankIfStr(filter) ? "" : " WHERE ".concat(filter), value);
            }

        }
        return String.format(Objects.requireNonNull(env.getProperty("JOB_TOPK_DEFAULT")),
                fieldName, fn, column, value, tableName, StrUtil.isBlankIfStr(filter) ? "" : " WHERE ".concat(filter), value);
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.env = environment;
    }
}
