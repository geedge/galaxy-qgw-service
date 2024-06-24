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
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.ExplainStatement;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.joda.time.Period;

import java.util.*;

public class AutoPeriodHelper {
    private static final Log log = LogFactory.get();
    private static final DatabaseService databaseService = (DatabaseService) SpringContextUtil.getBean("databaseService");
    private static final String TIME_FLOOR_WITH_FILL = "TIME_FLOOR_WITH_FILL";
    private static final String RATE = "RATE";
    private static final String CHART_GRANULARITY = "CHART_GRANULARITY";
    private static final String SAMPLE_GRANULARITY = "SAMPLE_GRANULARITY";
    private static final String SCHEMA_DOC_MEASUREMENTS_KEY = "measurements";
    private static final String SKIP_COMPACTION_OFFSET_FROM_LATEST = "skip_compaction_offset_from_latest";
    private static final String COMPACTION_PERIOD = "compaction_period";
    private static final String INGESTION_PERIOD = "ingestion_period";
    private static final String PT0S = "PT0S";
    private static final String PT1S = "PT1S";
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
        Map<String, Map<String, Object>> measurements = getMeasurementsMap(dslQueryRequestParam);
        if (measurements.isEmpty()) {
            return;
        }
        String granularity = dslQueryRequestParam.getGranularity();
        if (StrUtil.isEmpty(granularity)) {
            List<String> intervals = dslQueryRequestParam.getIntervals();
            if (intervals != null && intervals.size() == 1) {
                String[] split = intervals.get(0).split("/");
                DateTime start = DateUtil.parse(split[0]);
                DateTime end = DateUtil.parse(split[1]);
                dslQueryRequestParam.setGranularity(getGranularityPeriod(new DateTime(), start, end, measurements, true));
            }
        } else {
            if (granularity.startsWith(CHART_GRANULARITY)) {
                dslQueryRequestParam.setGranularity(parseGranularity(granularity, measurements, dslQueryRequestParam, true));
            } else if (granularity.startsWith(SAMPLE_GRANULARITY)) {
                parseGranularity(granularity, measurements, dslQueryRequestParam, false);
            }
        }
    }

    private static void buildSelectStatement(DateTime now, Select select) {
        TablesNamesFinder tablesNamesFinder = new TablesNamesFinder();
        List<String> tableList = tablesNamesFinder.getTableList(select);
        Map<String, Map<String, Object>> measurements = Maps.newHashMap();
        for (String table : tableList) {
            String measurement = databaseService.getValueByKeyInSchemaDoc(table, SCHEMA_DOC_MEASUREMENTS_KEY);
            if (StrUtil.isNotEmpty(measurement)) {
                measurements.put(table, JSON.parseObject(measurement, Map.class));
            }
        }
        select.getSelectBody().accept(new SelectAdapter(now, measurements));
    }

    private static String parseGranularity(String granularity, Map<String, Map<String, Object>> measurements, DSLQueryRequestParam dslQueryRequestParam, boolean isChartGranularity) {
        try {
            Expression expression = CCJSqlParserUtil.parseExpression(granularity);
            if (expression instanceof Function) {
                Function fun = (Function) expression;
                Expression expression1 = fun.getParameters().getExpressions().get(0);
                Expression expression2 = fun.getParameters().getExpressions().get(1);
                DateTime start = DateUtil.parse(((StringValue) expression1).getValue());
                DateTime end = DateUtil.parse(((StringValue) expression2).getValue());
                return getGranularityPeriod(new DateTime(), start, end, measurements, isChartGranularity);
            }
        } catch (JSQLParserException e) {
            log.error("Parse DSL auto granularity error", e);
        }
        return dslQueryRequestParam.getGranularity();
    }

    private static Map<String, Map<String, Object>> getMeasurementsMap(DSLQueryRequestParam dslQueryRequestParam) {
        if (DslIdentifierNameConst.IP_LEARNING_FQDN_RELATE_IP.equalsIgnoreCase(dslQueryRequestParam.getName())
                || DslIdentifierNameConst.IP_LEARNING_ACTIVE_IP.equalsIgnoreCase(dslQueryRequestParam.getName())) {
            return Maps.newHashMap();
        }
        Map<String, Map<String, Object>> measurements = Maps.newHashMap();
        if (StrUtil.isEmpty(dslQueryRequestParam.getDataSource())) {
            Set<String> tables = DslIdentifierNameConst.IDENTIFIER_NAME_SOURCE_MAPPING.get(dslQueryRequestParam.getName());
            if (tables != null && !tables.isEmpty()) {
                for (String table : tables) {
                    String measurement = databaseService.getValueByKeyInSchemaDoc(table, SCHEMA_DOC_MEASUREMENTS_KEY);
                    if (StrUtil.isNotEmpty(measurement)) {
                        measurements.put(table, JSON.parseObject(measurement, Map.class));
                    }
                }
            }
        } else {
            String measurement = databaseService.getValueByKeyInSchemaDoc(dslQueryRequestParam.getDataSource(), SCHEMA_DOC_MEASUREMENTS_KEY);
            if (StrUtil.isNotEmpty(measurement)) {
                measurements.put(dslQueryRequestParam.getDataSource(), JSON.parseObject(measurement, Map.class));
            }
        }
        return measurements;
    }

    private static String getGranularityPeriod(DateTime now, DateTime startTime, DateTime endTime, Map<String, Map<String, Object>> measurements, boolean isChartGranularity) {
        Map<String, String> measurement = getMeasurementWithEffectiveGranularity(measurements);
        long duration = DateUtil.between(startTime, endTime, DateUnit.SECOND, false);
        DateTime agoTime = DateUtil.offsetSecond(now, -Period.parse(measurement.get(SKIP_COMPACTION_OFFSET_FROM_LATEST)).toStandardSeconds().getSeconds());
        String dataPeriod = getDataPeriod(measurement, agoTime, startTime);
        int dataPeriodSeconds = Period.parse(dataPeriod).toStandardSeconds().getSeconds();
        return getStandardPeriod(isChartGranularity, duration, dataPeriodSeconds);
    }

    private static Map<String, String> getMeasurementWithEffectiveGranularity(Map<String, Map<String, Object>> measurements) {
        Map<String, String> measurement = Maps.newHashMap();
        measurements.values().forEach(m -> {
            m.forEach((k, v) -> {
                if (!SKIP_COMPACTION_OFFSET_FROM_LATEST.equalsIgnoreCase(k)
                        && !COMPACTION_PERIOD.equalsIgnoreCase(k)
                        && !INGESTION_PERIOD.equalsIgnoreCase(k)) {
                    return;
                }
                if (measurement.containsKey(k)) {
                    String oldPeriod = measurement.get(k);
                    if (SKIP_COMPACTION_OFFSET_FROM_LATEST.equalsIgnoreCase(k)) {
                        if (Period.parse(v.toString()).toStandardSeconds().getSeconds() < Period.parse(oldPeriod).toStandardSeconds().getSeconds()) {
                            measurement.put(k, v.toString());
                        }
                    } else {
                        if (Period.parse(v.toString()).toStandardSeconds().getSeconds() > Period.parse(oldPeriod).toStandardSeconds().getSeconds()) {
                            measurement.put(k, v.toString());
                        }
                    }
                } else {
                    measurement.put(k, v.toString());
                }
            });
        });
        setDefaultValues(measurement);
        return measurement;
    }

    private static void setDefaultValues(Map<String, String> measurement) {
        Arrays.asList(COMPACTION_PERIOD, INGESTION_PERIOD, SKIP_COMPACTION_OFFSET_FROM_LATEST)
                .forEach(key -> measurement.putIfAbsent(key, getDefaultValue(key)));
    }

    private static String getDefaultValue(String key) {
        switch (key) {
            case COMPACTION_PERIOD:
            case INGESTION_PERIOD:
                return PT1S;
            case SKIP_COMPACTION_OFFSET_FROM_LATEST:
                return PT0S;
            default:
                throw new IllegalArgumentException("Unknown key: " + key);
        }
    }

    private static String getDataPeriod(Map<String, String> measurement, DateTime splitTime, DateTime startTime) {
        String dataPeriod = null;
        if (DateUtil.compare(splitTime, startTime) > 0) {
            dataPeriod = measurement.get(COMPACTION_PERIOD);
        }
        if (StrUtil.isEmpty(dataPeriod)) {
            dataPeriod = measurement.get(INGESTION_PERIOD);
        }
        return dataPeriod;
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
        private final Map<String, Map<String, Object>> measurements;

        public SelectAdapter(DateTime now, Map<String, Map<String, Object>> measurements) {
            this.now = now;
            this.measurements = measurements;
        }

        @Override
        public void visit(PlainSelect plainSelect) {
            List<SelectItem> selectItems = plainSelect.getSelectItems();
            if (selectItems != null) {
                for (SelectItem selectItem : selectItems) {
                    SelectItemAdapter selectItemAdapter = new SelectItemAdapter(now, measurements);
                    selectItem.accept(selectItemAdapter);
                }
            }

            FromItem fromItem = plainSelect.getFromItem();
            if (fromItem != null) {
                fromItem.accept(new FromItemAdapter(now, measurements));
            }

            Expression where = plainSelect.getWhere();
            if (where != null) {
                where.accept(new ExpressionAdapter(now, measurements));
            }

            GroupByElement groupBy = plainSelect.getGroupBy();
            if (groupBy != null && groupBy.getGroupByExpressionList() != null && !groupBy.getGroupByExpressionList().getExpressions().isEmpty()) {
                groupBy.accept(new GroupByAdapter(now, measurements));
            }

            Expression having = plainSelect.getHaving();
            if (having != null) {
                having.accept(new ExpressionAdapter(now, measurements));
            }

            List<OrderByElement> orderByElements = plainSelect.getOrderByElements();
            if (orderByElements != null) {
                for (OrderByElement orderByElement : orderByElements) {
                    orderByElement.accept(new OrderByAdapter(now, measurements));
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
        private final Map<String, Map<String, Object>> measurements;

        public SelectItemAdapter(DateTime now, Map<String, Map<String, Object>> measurements) {
            this.now = now;
            this.measurements = measurements;
        }

        @Override
        public void visit(SelectExpressionItem selectExpressionItem) {
            Expression expression = selectExpressionItem.getExpression();
            FunctionAdapter functionAdapter = new FunctionAdapter(now, measurements);
            expression.accept(functionAdapter);
        }

    }

    private static class FromItemAdapter extends FromItemVisitorAdapter {
        private final DateTime now;
        private final Map<String, Map<String, Object>> measurements;

        public FromItemAdapter(DateTime now, Map<String, Map<String, Object>> measurements) {
            this.now = now;
            this.measurements = measurements;
        }

        @Override
        public void visit(SubSelect subSelect) {
            SelectBody selectBody = subSelect.getSelectBody();
            selectBody.accept(new SelectAdapter(now, measurements));
        }
    }

    private static class ExpressionAdapter extends ExpressionVisitorAdapter {
        private final DateTime now;
        private final Map<String, Map<String, Object>> measurements;

        public ExpressionAdapter(DateTime now, Map<String, Map<String, Object>> measurements) {
            this.now = now;
            this.measurements = measurements;
        }

        @Override
        public void visit(SubSelect subSelect) {
            SelectBody selectBody = subSelect.getSelectBody();
            selectBody.accept(new SelectAdapter(now, measurements));
        }

        @Override
        public void visit(Function function) {
            FunctionAdapter functionAdapter = new FunctionAdapter(now, measurements);
            function.accept(functionAdapter);
        }
    }

    private static class GroupByAdapter implements GroupByVisitor {
        private final DateTime now;
        private final Map<String, Map<String, Object>> measurements;

        public GroupByAdapter(DateTime now, Map<String, Map<String, Object>> measurements) {
            this.now = now;
            this.measurements = measurements;
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
                FunctionAdapter functionAdapter = new FunctionAdapter(now, measurements);
                expression.accept(functionAdapter);
            }
        }
    }

    private static class OrderByAdapter implements OrderByVisitor {
        private final DateTime now;
        private final Map<String, Map<String, Object>> measurements;

        public OrderByAdapter(DateTime now, Map<String, Map<String, Object>> measurements) {
            this.now = now;
            this.measurements = measurements;
        }

        @Override
        public void visit(OrderByElement orderBy) {
            if (orderBy.getExpression() == null) {
                return;
            }
            if (!(orderBy.getExpression() instanceof Function)) {
                return;
            }
            FunctionAdapter functionAdapter = new FunctionAdapter(now, measurements);
            orderBy.getExpression().accept(functionAdapter);

        }
    }

    private static class FunctionAdapter extends ExpressionVisitorAdapter {

        private final DateTime now;
        private final Map<String, Map<String, Object>> measurements;

        public FunctionAdapter(DateTime now, Map<String, Map<String, Object>> measurements) {
            this.now = now;
            this.measurements = measurements;
        }

        @Override
        public void visit(Function function) {
            if (isTimeFloorWithFill(function)) {
                buildNewFunction(function);
            } else if (isRate(function)) {
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
                String chartGranularityPeriod = getGranularityPeriod(now, startTime, endTime, measurements, true);
                function.getParameters().getExpressions().set(1, new StringValue("'" + chartGranularityPeriod + "'"));
            } else if (isSampleGranularity(period)) {
                Function periodFunction = (Function) period;
                DateTime startTime = DateUtil.parse(((StringValue) periodFunction.getParameters().getExpressions().get(0)).getValue());
                DateTime endTime = DateUtil.parse(((StringValue) periodFunction.getParameters().getExpressions().get(1)).getValue());
                String chartGranularityPeriod = getGranularityPeriod(now, startTime, endTime, measurements, false);
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

}
