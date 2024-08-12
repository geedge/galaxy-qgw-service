package com.mesalab.qgw.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Lists;
import com.jayway.jsonpath.JsonPath;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.enums.HttpStatusCodeEnum;
import com.mesalab.common.enums.MetricType;
import com.mesalab.common.enums.QueryOption;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.common.utils.sqlparser.SQLFunctionUtil;
import com.mesalab.qgw.constant.DataTypeConst;
import com.mesalab.qgw.model.basic.DSLQueryContext;
import com.mesalab.qgw.model.basic.DSLQueryRequestParam;
import com.mesalab.qgw.model.basic.SQLQueryContext;
import com.mesalab.qgw.service.CustomizedStatisticsService;
import com.mesalab.qgw.service.DatabaseService;
import com.mesalab.qgw.service.SQLSyncQueryService;
import com.mesalab.services.common.property.SqlPropertySourceFactory;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * TODO
 *
 * @Classname CustomizedStatisticsServiceImpl
 * @Date 2024/7/12 13:49
 * @Author wWei
 */
@Service
@PropertySource(value = "classpath:dsl-sql-template.sql", factory = SqlPropertySourceFactory.class)
public class CustomizedStatisticsServiceImpl implements CustomizedStatisticsService {
    private static final String METRICS = "custom.statistics.metrics";
    private static final String DIMENSIONS = "custom.statistics.dimensions";
    private static final String SUBQUERY_TOPK = "custom.statistics.subquery.topk";
    private static final String SUBQUERY_TOPK_DIMENSIONS = "dimensions";
    private static final String SUBQUERY_TOPK_METRIC = "metric";
    private static final String SUBQUERY_TOPK_LIMIT = "limit";
    private static final Integer SUBQUERY_TOPK_LIMIT_DEFAULT_VALUE = 10;
    private static final String CUSTOMIZED_STATISTICS = "CUSTOMIZED_STATISTICS";
    private static final String CUSTOMIZED_STATISTICS_SUBQUERY_TOPK = "CUSTOMIZED_STATISTICS_SUBQUERY_TOPK";
    private static final String LABEL = "label";
    private static final String DIMENSION_NAME = "dimension_name";
    private static final String METRIC_TYPE = "metric_type";

    private Environment environment;
    private DatabaseService databaseService;
    private SQLSyncQueryService sqlSyncQueryService;

    @Override
    public BaseResult run(DSLQueryRequestParam param) {
        boolean isTimeSeries = false;
        String partitionKey = databaseService.getPartitionKey(param.getDataSource());
        LinkedHashMap<String, Object> schemaByName = databaseService.getSchemaByName(param.getDataSource());
        List<String> partitionKeyLogicalTypes = JsonPath.read(schemaByName, "$.fields[?(@.name == \"" + partitionKey + "\")].type.logicalType");
        String partitionKeyLogicalType = partitionKeyLogicalTypes.isEmpty() ? null : partitionKeyLogicalTypes.get(0);
        List<String> times = JsonPath.read(schemaByName, "$.doc.schema_query.time");
        Map<String, Object> customRequestParam = param.getCustomRequestParam();
        List<String> selectItems = Lists.newArrayList();
        List<String> groupByItems = Lists.newArrayList();
        if (customRequestParam.get(DIMENSIONS) != null) {
            List<Map<String, Object>> dimensions = (List<Map<String, Object>>) customRequestParam.get(DIMENSIONS);
            for (Map<String, Object> dimension : dimensions) {
                CustomStatisticsObj customStatisticsObj = BeanUtil.copyProperties(dimension, CustomStatisticsObj.class);
                if (times.contains(customStatisticsObj.getDimensionName())
                        && customStatisticsObj.getFunction() != null
                        && (SQLFunctionUtil.TIME_FLOOR_WITH_FILL.equals(customStatisticsObj.getFunction().getName())
                        || SQLFunctionUtil.DATETIME_FLOOR_WITH_FILL.equals(customStatisticsObj.getFunction().getName()))) {
                    isTimeSeries = true;
                }
                selectItems.add(customStatisticsObj.buildSelectItem(isTimeSeries, param.getStart(), param.getEnd(), partitionKeyLogicalType));
                groupByItems.add(customStatisticsObj.buildExprItem(isTimeSeries, param.getStart(), param.getEnd(), partitionKeyLogicalType));
            }
        }

        if (customRequestParam.get(METRICS) != null) {
            List<Map<String, Object>> metrics = (List<Map<String, Object>>) customRequestParam.get(METRICS);
            for (Map<String, Object> metric : metrics) {
                CustomStatisticsObj customStatisticsObj = BeanUtil.copyProperties(metric, CustomStatisticsObj.class);
                selectItems.add(customStatisticsObj.buildSelectItem(isTimeSeries, param.getStart(), param.getEnd(), partitionKeyLogicalType));
            }
        }

        String subquery = null;
        if (customRequestParam.get(SUBQUERY_TOPK) != null) {
            subquery = buildSubQuery(param, partitionKeyLogicalType, times);
        }
        DSLQueryContext dslQueryContext = BeanUtil.copyProperties(param, DSLQueryContext.class);
        String sqlTemp = dslQueryContext.toSql(
                environment.getProperty(CUSTOMIZED_STATISTICS)
                , param.getDataSource()
                , databaseService.getPartitionKey(param.getDataSource())
                , partitionKeyLogicalType);
        String orderBy = param.getOrderBy();
        String sql = String.format(sqlTemp
                , String.join(", ", selectItems)
                , subquery == null ? "" : " AND " + subquery
                , groupByItems.isEmpty() ? "" : "GROUP BY " + String.join(", ", groupByItems)
                , StrUtil.isEmpty(orderBy) ? "" : "ORDER BY " + orderBy);
        String queryOption = param.isDryRun() ? QueryOption.SYNTAX_VALIDATION.getValue() : QueryOption.REAL_TIME.getValue();
        return sqlSyncQueryService.executeQuery(SQLQueryContext.builder().originalSQL(sql).option(queryOption).build());
    }

    private String buildSubQuery(DSLQueryRequestParam param, String logicalType, List<String> times) {
        Map<String, Object> customRequestParam = param.getCustomRequestParam();
        Map<String, Object> subQueryTopK = (Map<String, Object>) customRequestParam.get(SUBQUERY_TOPK);
        List<Map<String, Object>> dimensions = (List<Map<String, Object>>) customRequestParam.get(DIMENSIONS);
        List<String> dimensionLabelsOfSubQuery = (List<String>) subQueryTopK.get(SUBQUERY_TOPK_DIMENSIONS);
        List<Map<String, Object>> subQueryDimensions = Lists.newArrayList();
        if (dimensionLabelsOfSubQuery == null) {
            Iterator<Map<String, Object>> iterator = dimensions.iterator();
            while (iterator.hasNext()) {
                Map<String, Object> next = iterator.next();
                if (times.contains(String.valueOf(next.get(DIMENSION_NAME)))) {
                    iterator.remove();
                    break;
                }
            }
            if (dimensions.size() < 1) {
                throw new BusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getCode(),
                        String.format(CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getMessage(), "Get top-k dimension error"));
            }
            subQueryDimensions.add(dimensions.get(0));
        } else {
            subQueryDimensions.addAll(dimensions.stream()
                    .filter(dimension -> dimensionLabelsOfSubQuery.contains(String.valueOf(dimension.get(LABEL))))
                    .collect(Collectors.toList()));
        }

        Map<String, Object> orderByMetric;
        List<Map<String, Object>> metrics = (List<Map<String, Object>>) customRequestParam.get(METRICS);
        Object metric = subQueryTopK.get(SUBQUERY_TOPK_METRIC);
        orderByMetric = metric == null
                ? metrics.get(0)
                : metrics.stream().filter(m -> String.valueOf(m.get(LABEL)).equals(String.valueOf(metric))).findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getCode(),
                        String.format(CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getMessage(), "The top-k metric must be included in the Customized Statistics Metrics")));

        Integer limit = (Integer) subQueryTopK.get(SUBQUERY_TOPK_LIMIT);
        limit = limit == null ? SUBQUERY_TOPK_LIMIT_DEFAULT_VALUE : limit;

        List<String> subQuerySelectExprItems = Lists.newArrayList();
        for (Map<String, Object> dimension : subQueryDimensions) {
            CustomStatisticsObj customStatisticsObj = BeanUtil.copyProperties(dimension, CustomStatisticsObj.class);
            String item = customStatisticsObj.buildExprItem(false, param.getStart(), param.getEnd(), logicalType);
            subQuerySelectExprItems.add(item);
        }

        CustomStatisticsObj customStatisticsObj = BeanUtil.copyProperties(orderByMetric, CustomStatisticsObj.class);
        String subQueryOrderByMetric = customStatisticsObj.buildExprItem(false, param.getStart(), param.getEnd(), logicalType);

        DSLQueryContext dslQueryContext = BeanUtil.copyProperties(param, DSLQueryContext.class);
        dslQueryContext.setLimit(String.valueOf(limit));

        String sql = dslQueryContext.toSql(
                environment.getProperty(CUSTOMIZED_STATISTICS_SUBQUERY_TOPK)
                , param.getDataSource()
                , databaseService.getPartitionKey(param.getDataSource())
                , logicalType);
        return String.format(sql
                , String.join(", ", subQuerySelectExprItems)
                , String.join(", ", subQuerySelectExprItems)
                , subQuerySelectExprItems.isEmpty() ? "" : "GROUP BY " + String.join(", ", subQuerySelectExprItems)
                , StrUtil.isEmpty(subQueryOrderByMetric) ? "" : "ORDER BY " + subQueryOrderByMetric + "DESC");
    }

    @Override
    public void validate(DSLQueryRequestParam param) {
        Map<String, Object> customRequestParam = param.getCustomRequestParam();
        List<Map<String, Object>> dimensions = getListFromMapOrEmpty(customRequestParam, DIMENSIONS);
        List<Map<String, Object>> metrics = getListFromMapOrEmpty(customRequestParam, METRICS);
        validateDimensions(dimensions);
        validateMetrics(metrics);
        Map<String, Object> topKObj = getMapFromMapOrEmpty(customRequestParam, SUBQUERY_TOPK);
        validateTopK(dimensions, metrics, topKObj);
    }

    private void validateDimensions(List<Map<String, Object>> dimensions) {
        if (!dimensions.stream().allMatch(dim -> dim.containsKey(LABEL))) {
            throw new BusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getMessage(), "Customized Statistics Dimensions must define a Label"));
        }
        dimensions.forEach(CustomizedStatisticsServiceImpl::validateMetricType);
    }

    private void validateMetrics(List<Map<String, Object>> metrics) {
        if (!metrics.stream().allMatch(dim -> dim.containsKey(LABEL))) {
            throw new BusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getMessage(), "Customized Statistics Metrics must define a Label"));
        }
        metrics.forEach(CustomizedStatisticsServiceImpl::validateMetricType);

    }

    private static void validateMetricType(Map<String, Object> metric) {
        Object metricType = metric.get(METRIC_TYPE);
        if (metricType != null) {
            MetricType metricTypeByValue = MetricType.getMetricTypeByValue(String.valueOf(metricType));
            if (metricTypeByValue == null) {
                throw new BusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getCode(),
                        String.format(CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getMessage(), "The metric type is not supported"));
            }
        }
    }

    private void validateTopK(List<Map<String, Object>> dimensions, List<Map<String, Object>> metrics, Map<String, Object> topKMap) {
        if (topKMap != null) {
            List<String> topKDimensions = getListFromMapOrEmpty(topKMap, SUBQUERY_TOPK_DIMENSIONS);
            if (topKDimensions.size() > 1) {
                throw new BusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getCode(),
                        String.format(CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getMessage(), "The top-k limited to one dimension"));
            }
            if (!topKDimensions.stream().allMatch(d -> dimensions.stream()
                    .anyMatch(dim -> d.equals(dim.get(LABEL))))) {
                throw new BusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getCode(),
                        String.format(CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getMessage(), "The top-k dimensions must be included in the Customized Statistics Dimensions"));
            }
            Object metric = topKMap.get(SUBQUERY_TOPK_METRIC);
            if (metric != null) {
                if (metrics.stream().noneMatch(m -> StrUtil.equals(String.valueOf(metric), String.valueOf(m.get(LABEL))))) {
                    throw new BusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getCode(),
                            String.format(CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getMessage(), "The top-k metric must be included in the Customized Statistics Metrics"));
                }
            }
        }
    }

    private <K, V> Map<K, V> getMapFromMapOrEmpty(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<K, V>) value;
        }
        return Collections.emptyMap();
    }

    private <T> List<T> getListFromMapOrEmpty(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List) {
            return (List<T>) value;
        }
        return Collections.emptyList();
    }

    @Data
    class CustomStatisticsObj {
        private Function function;
        @JsonProperty("metric_name")
        private String metricName;
        @JsonProperty("dimension_name")
        private String dimensionName;
        @JsonProperty("metric_type")
        private String metricType;
        private String expression;
        private String label;

        private String getName() {
            return metricName == null ? dimensionName : metricName;
        }

        @Data
        class Function {
            private String name;
            private Object[] variables;
        }

        public String buildSelectItem(boolean isTimeSeries, DateTime start, DateTime end, String logicalType) {
            String expr = buildExprItem(isTimeSeries, start, end, logicalType);
            return String.format("%s AS \"%s\"", expr, label);
        }

        public String buildExprItem(boolean isTimeSeries, DateTime start, DateTime end, String logicalType) {
            if (expression != null) {
                return expression;
            }
            if (function == null) {
                return getName();
            }
            String funNewName = SQLFunctionUtil.generateFunctionWithMetricType(function.getName(), metricType);
            if (StrUtil.equalsIgnoreCase(SQLFunctionUtil.TIME_FLOOR_WITH_FILL, function.getName())
                    || StrUtil.equalsIgnoreCase(SQLFunctionUtil.DATETIME_FLOOR_WITH_FILL, function.getName())) {
                if (StrUtil.equalsIgnoreCase(DataTypeConst.TIMESTAMP, logicalType)) {
                    return String.format("%s(UNIX_TIMESTAMP(%s), CHART_GRANULARITY('%s', '%s'), 'zero')",
                            funNewName, getName(), start, end);
                } else {
                    return String.format("%s(%s, CHART_GRANULARITY('%s', '%s'), 'zero')",
                            funNewName, getName(), start, end);
                }
            } else if (StrUtil.equalsIgnoreCase(SQLFunctionUtil.RATE, function.getName()) || StrUtil.equalsIgnoreCase(SQLFunctionUtil.BITRATE, function.getName())) {
                Object[] variables = function.getVariables();
                boolean hasVariables = variables != null && variables.length >= 1;
                long intervalSeconds = (end.getTime() - start.getTime()) / 1000;
                if (isTimeSeries) {
                    return hasVariables ? String.format("%s(%s, CHART_GRANULARITY('%s', '%s'), %s)", funNewName, getName(), start, end, variables[0])
                            : String.format("%s(%s, CHART_GRANULARITY('%s', '%s'))", funNewName, getName(), start, end);
                } else {
                    return hasVariables ? String.format("%s(%s, %s, %s)", funNewName, getName(), intervalSeconds, variables[0])
                            : String.format("%s(%s, %s)", funNewName, getName(), intervalSeconds);
                }
            } else {
                return String.format("%s(%s)", funNewName, getName());
            }
        }
    }

    @Autowired
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Autowired
    public void setDatabaseService(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    @Autowired
    public void setSQLSyncQueryService(SQLSyncQueryService sqlSyncQueryService) {
        this.sqlSyncQueryService = sqlSyncQueryService;
    }

}
