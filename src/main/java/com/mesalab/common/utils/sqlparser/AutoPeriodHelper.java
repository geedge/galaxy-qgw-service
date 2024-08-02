package com.mesalab.common.utils.sqlparser;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson2.JSON;
import com.google.common.collect.Maps;
import com.mesalab.common.utils.SpringContextUtil;
import com.mesalab.qgw.constant.DslIdentifierNameConst;
import com.mesalab.qgw.model.basic.DSLQueryRequestParam;
import com.mesalab.qgw.service.DatabaseService;
import lombok.Getter;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.ExplainStatement;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;
import org.joda.time.Period;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import java.util.*;

public class AutoPeriodHelper {
    private static final Log log = LogFactory.get();
    private static final DatabaseService databaseService = (DatabaseService) SpringContextUtil.getBean("databaseService");
    private static final String TIME_FLOOR_WITH_FILL = "TIME_FLOOR_WITH_FILL";
    private static final String RATE = "RATE";
    private static final String BITRATE = "BITRATE";
    private static final String CHART_GRANULARITY = "CHART_GRANULARITY";
    private static final String SAMPLE_GRANULARITY = "SAMPLE_GRANULARITY";
    private static final String SCHEMA_DOC_MEASUREMENTS_KEY = "measurements";
    private static final String RETENTIONS = "retentions";
    private static final String DEFAULT_STORAGE_PERIOD_GRANULARITY_PT1S = "PT1S";
    private static final String DEFAULT_STORAGE_SCHEMA_GRANULARITY = "1s";
    private static final String DEFAULT_STORAGE_SCHEMA_MAX_DURATION = Integer.MAX_VALUE + StorageSchemaRetentionUnitEnum.DAY.getStorageUnit();
    private static final String P1D = "P1D";
    private static final Map<Long, String> STANDARD_CHART_PERIOD_MAP = new TreeMap<>();
    private static final Map<Long, String> STANDARD_SAMPLE_PERIOD_MAP = new TreeMap<>();

    static {
        STANDARD_CHART_PERIOD_MAP.put(300L, "PT1S");
        STANDARD_CHART_PERIOD_MAP.put(3600L, "PT30S");
        STANDARD_CHART_PERIOD_MAP.put(3600 * 12L, "PT1M");
        STANDARD_CHART_PERIOD_MAP.put(86400 * 3L, "PT5M");
        STANDARD_CHART_PERIOD_MAP.put(86400 * 30L, "PT1H");
        STANDARD_CHART_PERIOD_MAP.put(Long.MAX_VALUE, "P1D");
        STANDARD_SAMPLE_PERIOD_MAP.put(3600L, "PT1S");
        STANDARD_SAMPLE_PERIOD_MAP.put(3600 * 12L, "PT30S");
        STANDARD_SAMPLE_PERIOD_MAP.put(86400 * 3L, "PT1M");
        STANDARD_SAMPLE_PERIOD_MAP.put(86400 * 30L, "PT5M");
        STANDARD_SAMPLE_PERIOD_MAP.put(Long.MAX_VALUE, "PT1H");
    }

    private AutoPeriodHelper() {
    }

    public static Statement buildSqlGranularity(Statement statement) {
        DateTime now = new DateTime();
        if (statement instanceof ExplainStatement) {
            ExplainStatement explainStatement = (ExplainStatement) statement;
            buildSelectStatement(now, explainStatement.getStatement());
            return explainStatement;
        } else if (statement instanceof Select) {
            buildSelectStatement(now, (Select) statement);
            return statement;
        }
        return statement;
    }

    public static void buildDslGranularity(DSLQueryRequestParam dslQueryRequestParam) {
        Map<String, String> retentionMap = getRetentionMap(dslQueryRequestParam);
        if (retentionMap.isEmpty()) {
            return;
        }
        String granularity = dslQueryRequestParam.getGranularity();
        if (StrUtil.isEmpty(granularity)) {
            List<String> intervals = dslQueryRequestParam.getIntervals();
            if (intervals != null && intervals.size() == 1) {
                String[] split = intervals.get(0).split("/");
                DateTime start = DateUtil.parse(split[0]);
                DateTime end = DateUtil.parse(split[1]);
                dslQueryRequestParam.setGranularity(getGranularityPeriod(new DateTime(), start, end, retentionMap, true));
            }
        } else {
            if (granularity.startsWith(CHART_GRANULARITY)) {
                dslQueryRequestParam.setGranularity(parseGranularity(granularity, retentionMap, dslQueryRequestParam, true));
            } else if (granularity.startsWith(SAMPLE_GRANULARITY)) {
                parseGranularity(granularity, retentionMap, dslQueryRequestParam, false);
            }
        }
    }

    private static void buildSelectStatement(DateTime now, Select select) {
        Map<String, String> retentionMap = Maps.newHashMap();
        List<String> tableList = SQLHelper.getTableName(select);
        for (String table : tableList) {
            buildRetentionMap(retentionMap, table);
        }
        select.getSelectBody().accept(new SelectAdapter(now, retentionMap));
    }

    private static void buildRetentionMap(Map<String, String> retentionMap, String table) {
        String measurement = databaseService.getValueByKeyInSchemaDoc(table, SCHEMA_DOC_MEASUREMENTS_KEY);
        if (StrUtil.isNotBlank(measurement)) {
            Map map = JSON.parseObject(measurement, Map.class);
            Object retentions = map.get(RETENTIONS);
            if (StrUtil.isBlankIfStr(retentions)) {
                retentionMap.put(table, getDefaultRetention());
            } else {
                retentionMap.put(table, String.valueOf(retentions));
            }
        } else {

        }
    }

    private static String parseGranularity(String granularity, Map<String, String> retentionMap, DSLQueryRequestParam dslQueryRequestParam, boolean isChartGranularity) {
        try {
            Expression expression = CCJSqlParserUtil.parseExpression(granularity);
            if (expression instanceof Function) {
                Function fun = (Function) expression;
                Expression expression1 = fun.getParameters().getExpressions().get(0);
                Expression expression2 = fun.getParameters().getExpressions().get(1);
                DateTime start = DateUtil.parse(((StringValue) expression1).getValue());
                DateTime end = DateUtil.parse(((StringValue) expression2).getValue());
                return getGranularityPeriod(new DateTime(), start, end, retentionMap, isChartGranularity);
            }
        } catch (JSQLParserException e) {
            log.error("Parse DSL auto granularity error", e);
        }
        return dslQueryRequestParam.getGranularity();
    }

    private static Map<String, String> getRetentionMap(DSLQueryRequestParam dslQueryRequestParam) {
        Map<String, String> retentionMap = Maps.newHashMap();

        if (DslIdentifierNameConst.IP_LEARNING_FQDN_RELATE_IP.equalsIgnoreCase(dslQueryRequestParam.getName())
                || DslIdentifierNameConst.IP_LEARNING_ACTIVE_IP.equalsIgnoreCase(dslQueryRequestParam.getName())) {
            return retentionMap;
        }
        if (StrUtil.isBlank(dslQueryRequestParam.getDataSource())) {
            Set<String> tables = DslIdentifierNameConst.IDENTIFIER_NAME_SOURCE_MAPPING.get(dslQueryRequestParam.getName());
            if (tables != null && !tables.isEmpty()) {
                for (String table : tables) {
                    buildRetentionMap(retentionMap, table);
                }
            }
        } else {
            buildRetentionMap(retentionMap, dslQueryRequestParam.getDataSource());
        }
        return retentionMap;
    }

    private static String getGranularityPeriod(DateTime now, DateTime startTime, DateTime endTime, Map<String, String> retentionMap, boolean isChartGranularity) {
        long duration = DateUtil.between(startTime, endTime, DateUnit.SECOND, false);
        String dataPeriod = DEFAULT_STORAGE_PERIOD_GRANULARITY_PT1S;
        for (String retention : retentionMap.values()) {
            String dataPeriodTmp = getStoragePeriod(startTime, now, retention);
            int secondsTemp = Period.parse(dataPeriodTmp).toStandardSeconds().getSeconds();
            int seconds = Period.parse(dataPeriod).toStandardSeconds().getSeconds();
            if (secondsTemp > seconds) {
                dataPeriod = dataPeriodTmp;
            }
        }

        int dataPeriodSeconds = Period.parse(dataPeriod).toStandardSeconds().getSeconds();
        return getStandardPeriod(isChartGranularity, duration, dataPeriodSeconds);
    }

    private static String getStoragePeriod(DateTime filterStartTime, DateTime now, String retentions) {
        if (StrUtil.isBlank(retentions)) {
            retentions = getDefaultRetention();
        }
        String[] retentionArray = retentions.split(",");
        DateTime storageEndTime = new DateTime(now);

        for (String retention : retentionArray) {
            String[] item = retention.split(":");
            String storageGranularity = StrUtil.isBlank(item[0]) ? DEFAULT_STORAGE_SCHEMA_GRANULARITY : item[0].trim();
            String storageStartTimeStr = StrUtil.isBlank(item[1]) ? DEFAULT_STORAGE_SCHEMA_MAX_DURATION : item[1].trim();
            DateTime storageStartTime = getStorageStartTime(storageEndTime, storageStartTimeStr);
            String granularityValue = getStorageGranularity(filterStartTime, storageStartTime, storageGranularity);
            if (granularityValue != null) {
                return granularityValue;
            }
        }
        String retention = retentionArray[retentionArray.length - 1];
        String[] item = retention.split(":");
        String granularity = StrUtil.isBlank(item[0]) ? DEFAULT_STORAGE_SCHEMA_GRANULARITY : item[0].trim();
        return getPeriodByRetentionGranularity(granularity);
    }

    private static String getDefaultRetention() {
        return DEFAULT_STORAGE_SCHEMA_GRANULARITY + ":" + DEFAULT_STORAGE_SCHEMA_MAX_DURATION;
    }

    private static DateTime getStorageStartTime(DateTime storageEndTime, String storageStartTimeStr) {
        switch (getTimeUnit(storageStartTimeStr)) {
            case SECOND:
                int secondsToSubtract = Integer.parseInt(StrUtil.removeSuffixIgnoreCase(storageStartTimeStr, StorageSchemaRetentionUnitEnum.SECOND.getStorageUnit()));
                return DateUtil.offsetSecond(storageEndTime, -secondsToSubtract);
            case MINUTE:
                int minutesToSubtract = Integer.parseInt(StrUtil.removeSuffixIgnoreCase(storageStartTimeStr, StorageSchemaRetentionUnitEnum.MINUTE.getStorageUnit()));
                return DateUtil.offsetMinute(storageEndTime, -minutesToSubtract);
            case HOUR:
                int hoursToSubtract = Integer.parseInt(StrUtil.removeSuffixIgnoreCase(storageStartTimeStr, StorageSchemaRetentionUnitEnum.HOUR.getStorageUnit()));
                return DateUtil.offsetHour(storageEndTime, -hoursToSubtract);
            case DAY:
                int daysToSubtract = Integer.parseInt(StrUtil.removeSuffixIgnoreCase(storageStartTimeStr, StorageSchemaRetentionUnitEnum.DAY.getStorageUnit()));
                return DateUtil.offsetDay(storageEndTime, -daysToSubtract);
            default:
                throw new IllegalArgumentException("Invalid time unit: " + storageStartTimeStr);
        }
    }

    private static StorageSchemaRetentionUnitEnum getTimeUnit(String storageStartTimeStr) {
        for (StorageSchemaRetentionUnitEnum unit : StorageSchemaRetentionUnitEnum.values()) {
            if (storageStartTimeStr.endsWith(unit.getStorageUnit())) {
                return unit;
            }
        }
        throw new IllegalArgumentException("Invalid time unit: " + storageStartTimeStr);
    }

    private static String getPeriodByRetentionGranularity(String retentionGranularity) {
        StorageSchemaRetentionUnitEnum timeUnit = null;
        for (StorageSchemaRetentionUnitEnum unit : StorageSchemaRetentionUnitEnum.values()) {
            if (retentionGranularity.endsWith(unit.getStorageUnit())) {
                timeUnit = unit;
                break;
            }
        }

        if (timeUnit == null) {
            throw new IllegalArgumentException("Invalid time unit: " + retentionGranularity);
        }

        String value = retentionGranularity.replace(timeUnit.getStorageUnit(), "");
        return String.format(timeUnit.getPeriodFormat(), value);
    }

    @Nullable
    private static String getStorageGranularity(@NotNull DateTime filterStartTime, @NotNull DateTime storageStartTime, @NotNull String granularity) {
        if (filterStartTime.isAfterOrEquals(storageStartTime)) {
            return getPeriodByRetentionGranularity(granularity);
        }
        return null;
    }

    private static String getStandardPeriod(boolean isChartGranularity, long duration, int dataPeriodSeconds) {
        Map<Long, String> standard = isChartGranularity ? STANDARD_CHART_PERIOD_MAP : STANDARD_SAMPLE_PERIOD_MAP;
        for (Map.Entry<Long, String> entry : standard.entrySet()) {
            if (duration <= entry.getKey() && dataPeriodSeconds <= Period.parse(entry.getValue()).toStandardSeconds().getSeconds()) {
                return entry.getValue();
            }
        }
        return P1D;
    }

    private static class SelectAdapter extends SelectVisitorAdapter {
        private final DateTime now;
        private final Map<String, String> retentionMap;

        public SelectAdapter(DateTime now, Map<String, String> retentionMap) {
            this.now = now;
            this.retentionMap = retentionMap;
        }

        @Override
        public void visit(PlainSelect plainSelect) {
            List<SelectItem> selectItems = plainSelect.getSelectItems();
            if (selectItems != null) {
                for (SelectItem selectItem : selectItems) {
                    SelectItemAdapter selectItemAdapter = new SelectItemAdapter(now, retentionMap);
                    selectItem.accept(selectItemAdapter);
                }
            }

            FromItem fromItem = plainSelect.getFromItem();
            if (fromItem != null) {
                fromItem.accept(new FromItemAdapter(now, retentionMap));
            }

            Expression where = plainSelect.getWhere();
            if (where != null) {
                where.accept(new ExpressionAdapter(now, retentionMap));
            }

            GroupByElement groupBy = plainSelect.getGroupBy();
            if (groupBy != null && groupBy.getGroupByExpressionList() != null && !groupBy.getGroupByExpressionList().getExpressions().isEmpty()) {
                groupBy.accept(new GroupByAdapter(now, retentionMap));
            }

            Expression having = plainSelect.getHaving();
            if (having != null) {
                having.accept(new ExpressionAdapter(now, retentionMap));
            }

            List<OrderByElement> orderByElements = plainSelect.getOrderByElements();
            if (orderByElements != null) {
                for (OrderByElement orderByElement : orderByElements) {
                    orderByElement.accept(new OrderByAdapter(now, retentionMap));
                }
            }
        }

        @Override
        public void visit(SetOperationList setOpList) {
            for (SelectBody select : setOpList.getSelects()) {
                if (select instanceof PlainSelect) {
                    select.accept(this);
                }
            }
        }
    }

    private static class SelectItemAdapter extends SelectItemVisitorAdapter {

        private final DateTime now;
        private final Map<String, String> retentionMap;

        public SelectItemAdapter(DateTime now, Map<String, String> retentionMap) {
            this.now = now;
            this.retentionMap = retentionMap;
        }

        @Override
        public void visit(SelectExpressionItem selectExpressionItem) {
            Expression expression = selectExpressionItem.getExpression();
            FunctionAdapter functionAdapter = new FunctionAdapter(now, retentionMap);
            expression.accept(functionAdapter);
        }

    }

    private static class FromItemAdapter extends FromItemVisitorAdapter {
        private final DateTime now;
        private final Map<String, String> retentionMap;

        public FromItemAdapter(DateTime now, Map<String, String> retentionMap) {
            this.now = now;
            this.retentionMap = retentionMap;
        }

        @Override
        public void visit(SubSelect subSelect) {
            SelectBody selectBody = subSelect.getSelectBody();
            selectBody.accept(new SelectAdapter(now, retentionMap));
        }
    }

    private static class ExpressionAdapter extends ExpressionVisitorAdapter {
        private final DateTime now;
        private final Map<String, String> retentionMap;

        public ExpressionAdapter(DateTime now, Map<String, String> retentionMap) {
            this.now = now;
            this.retentionMap = retentionMap;
        }

        @Override
        public void visit(SubSelect subSelect) {
            SelectBody selectBody = subSelect.getSelectBody();
            selectBody.accept(new SelectAdapter(now, retentionMap));
        }

        @Override
        public void visit(Function function) {
            FunctionAdapter functionAdapter = new FunctionAdapter(now, retentionMap);
            function.accept(functionAdapter);
        }
    }

    private static class GroupByAdapter implements GroupByVisitor {
        private final DateTime now;
        private final Map<String, String> retentionMap;

        public GroupByAdapter(DateTime now, Map<String, String> retentionMap) {
            this.now = now;
            this.retentionMap = retentionMap;
        }

        @Override
        public void visit(GroupByElement groupByElement) {
            if (groupByElement.getGroupByExpressionList() == null
                    || groupByElement.getGroupByExpressionList().getExpressions() == null) {
                return;
            }
            for (Expression expression : groupByElement.getGroupByExpressionList().getExpressions()) {
                if (!(expression instanceof Function)) {
                    continue;
                }
                FunctionAdapter functionAdapter = new FunctionAdapter(now, retentionMap);
                expression.accept(functionAdapter);
            }
        }
    }

    private static class OrderByAdapter implements OrderByVisitor {
        private final DateTime now;
        private final Map<String, String> retentionMap;

        public OrderByAdapter(DateTime now, Map<String, String> retentionMap) {
            this.now = now;
            this.retentionMap = retentionMap;
        }

        @Override
        public void visit(OrderByElement orderBy) {
            if (orderBy.getExpression() == null) {
                return;
            }
            if (!(orderBy.getExpression() instanceof Function)) {
                return;
            }
            FunctionAdapter functionAdapter = new FunctionAdapter(now, retentionMap);
            orderBy.getExpression().accept(functionAdapter);

        }
    }

    private static class FunctionAdapter extends ExpressionVisitorAdapter {

        private final DateTime now;
        private final Map<String, String> retentionMap;

        public FunctionAdapter(DateTime now, Map<String, String> retentionMap) {
            this.now = now;
            this.retentionMap = retentionMap;
        }

        @Override
        public void visit(Function function) {
            if (isTimeFloorWithFill(function)) {
                buildNewFunction(function);
            } else if (isRate(function)) {
                buildNewFunction(function);
            } else if (isBitRate(function)) {
                buildNewFunction(function);
            } else {
                Optional.ofNullable(function.getParameters()).ifPresent(o -> {
                    for (Expression expression : o.getExpressions()) {
                        expression.accept(this);
                    }
                });
            }
        }

        private void buildNewFunction(Function function) {
            Expression period = function.getParameters().getExpressions().get(1);
            if (isChartGranularity(period)) {
                Function periodFunction = (Function) period;
                DateTime startTime = DateUtil.parse(((StringValue) periodFunction.getParameters().getExpressions().get(0)).getValue());
                DateTime endTime = DateUtil.parse(((StringValue) periodFunction.getParameters().getExpressions().get(1)).getValue());
                String chartGranularityPeriod = getGranularityPeriod(now, startTime, endTime, retentionMap, true);
                function.getParameters().getExpressions().set(1, new StringValue("'" + chartGranularityPeriod + "'"));
            } else if (isSampleGranularity(period)) {
                Function periodFunction = (Function) period;
                DateTime startTime = DateUtil.parse(((StringValue) periodFunction.getParameters().getExpressions().get(0)).getValue());
                DateTime endTime = DateUtil.parse(((StringValue) periodFunction.getParameters().getExpressions().get(1)).getValue());
                String chartGranularityPeriod = getGranularityPeriod(now, startTime, endTime, retentionMap, false);
                function.getParameters().getExpressions().set(1, new StringValue("'" + chartGranularityPeriod + "'"));
            }
        }

    }

    private static boolean isChartGranularity(Expression expression) {
        if (!(expression instanceof Function)) {
            return false;
        }
        Function function = (Function) expression;
        return CHART_GRANULARITY.equalsIgnoreCase(function.getName())
                && function.getParameters().getExpressions().size() == 2;
    }

    private static boolean isSampleGranularity(Expression expression) {
        if (!(expression instanceof Function)) {
            return false;
        }
        Function function = (Function) expression;
        return SAMPLE_GRANULARITY.equalsIgnoreCase(function.getName())
                && function.getParameters().getExpressions().size() == 2;
    }

    private static boolean isTimeFloorWithFill(Expression expression) {
        if (!(expression instanceof Function)) {
            return false;
        }
        Function function = (Function) expression;
        return TIME_FLOOR_WITH_FILL.equalsIgnoreCase(function.getName())
                && function.getParameters().getExpressions().size() >= 2;
    }

    private static boolean isRate(Expression expression) {
        if (!(expression instanceof Function)) {
            return false;
        }
        Function function = (Function) expression;
        return RATE.equalsIgnoreCase(function.getName())
                && function.getParameters().getExpressions().size() >= 2;
    }

    private static boolean isBitRate(Expression expression) {
        if (!(expression instanceof Function)) {
            return false;
        }
        Function function = (Function) expression;
        return BITRATE.equalsIgnoreCase(function.getName())
                && function.getParameters().getExpressions().size() >= 2;

    }

    @Getter
    private enum StorageSchemaRetentionUnitEnum {
        SECOND("s", "PT%sS"),
        MINUTE("m", "PT%sM"),
        HOUR("h", "PT%sH"),
        DAY("d", "P%sD");
        private final String storageUnit;
        private final String periodFormat;

        StorageSchemaRetentionUnitEnum(String storageUnit, String periodFormat) {
            this.storageUnit = storageUnit;
            this.periodFormat = periodFormat;
        }
    }

}
