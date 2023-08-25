package com.mesalab.qgw.model.basic.udf;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.DataTypeMapping;
import com.mesalab.common.utils.ConvertUtil;
import com.mesalab.common.utils.sqlparser.SQLFunctionUtil;
import com.mesalab.common.utils.SpringContextUtil;
import com.mesalab.common.utils.sqlparser.SQLHelper;
import com.mesalab.qgw.model.basic.EngineConfigSource;
import com.mesalab.qgw.model.basic.SQLQuerySource;
import com.mesalab.qgw.service.MetadataService;
import com.geedgenetworks.utils.DateUtils;
import com.geedgenetworks.utils.StringUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.Between;
import net.sf.jsqlparser.expression.operators.relational.ComparisonOperator;
import net.sf.jsqlparser.statement.select.*;
import org.joda.time.DateTime;

import java.util.*;

@AllArgsConstructor
@NoArgsConstructor
public class TIME_FLOOR_WITH_FILL implements UDF{
    private static final Log log = LogFactory.get();
    private MetadataService metadataService = (MetadataService) SpringContextUtil.getBean("metadataService");
    private EngineConfigSource engineConfigSource = (EngineConfigSource) SpringContextUtil.getBean("engineConfigSource");
    private String name;
    private String timestamp;
    private String period;
    private String fill;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public String getFill() {
        return fill;
    }

    public void setFill(String fill) {
        this.fill = fill;
    }

    public TIME_FLOOR_WITH_FILL(UDFElements udfElements) {
        this.name = udfElements.getName();

        this.timestamp = udfElements.getParams().get(0).toString();
        this.period = ((StringValue) udfElements.getParams().get(1)).getValue();
        if (udfElements.getParams().size() >= 3) {
            this.fill = ((StringValue) udfElements.getParams().get(2)).getValue();
        }
    }


    @Override
    public Object execute(SQLQuerySource sqlQuerySource, BaseResult baseResult) {
        List<Object> results = (List<Object>)baseResult.getData();
        List<Map<String, Object>> targetResult = Lists.newArrayList();
        List<String> groupLabels = Lists.newArrayList();
        String timeGranLabel = null;
        try {
            Map<String, String> groupDimension = sqlQuerySource.getGroupDimension();
            if (results.isEmpty() || StringUtil.isBlank(fill) || StringUtil.isEmpty(groupDimension)) {
                return results;
            }
            Map<String, Object> resultsFirst = (Map<String, Object>) results.get(0);
            if (!groupDimension.keySet().stream().allMatch(resultsFirst::containsKey)) {
                return results;
            }
            for (String groupKey : groupDimension.keySet()) {
                if (groupDimension.get(groupKey).contains(name) &&
                        groupDimension.get(groupKey).contains(timestamp)) {
                    timeGranLabel = groupKey;
                } else {
                    groupLabels.add(groupKey);
                }

            }

            WhereTimeRange whereTimeRange = getWhereTimeRange(sqlQuerySource);
            Date benchmarkDate = getBenchmarkDate(resultsFirst, timeGranLabel);

            targetResult = fillResultBasedOnTimeSeries(results, groupLabels, timeGranLabel);

            List<Date> dateRangeList = null;

            if (StringUtil.isNotBlank(whereTimeRange.getStart())) {

                if (StringUtil.isBlank(whereTimeRange.getEnd())) {
                    whereTimeRange.setEnd(DateUtils.getCurrentDate(DateUtils.YYYY_MM_DD_HH24_MM_SS));
                }

                List<Date> leftDateRange = DateUtils.getLeftDateRange(benchmarkDate,
                        DateUtil.parse(whereTimeRange.getStart()), period);
                List<Date> rightDateRange = DateUtils.getRightDateRange(benchmarkDate,
                        DateUtil.parse(whereTimeRange.getEnd()), period);

                Collections.reverse(leftDateRange);
                dateRangeList = Lists.newArrayList(Iterables.concat(leftDateRange, rightDateRange));

            } else {
                log.warn("The time range is empty [startTime:{},endTime:{}], so the time series data cannot be filled.", whereTimeRange.getStart(), whereTimeRange.getEnd());
                return targetResult;
            }
            if (StringUtil.isNotEmpty(dateRangeList)
                    && !whereTimeRange.isMinorThanEquals()
                    && DateUtil.toInstant(DateUtil.parse(whereTimeRange.getEnd()))
                    .compareTo(DateUtil.toInstant(dateRangeList.get(dateRangeList.size() - 1))) == 0) {
                dateRangeList.remove(dateRangeList.size() - 1);
            }
            if (StringUtil.isEmpty(dateRangeList)) {
                return targetResult;
            }
            int maxSeriesNum = engineConfigSource.getMaxCacheNum() / dateRangeList.size();
            if (maxSeriesNum == 0) {
                log.warn("初始时间范围超出系统设置，无法进行补全[getMaxCacheNum:{} - dateRangeNum:{}],补全功能无效!", engineConfigSource.getMaxCacheNum(), dateRangeList.size());
                return targetResult;
            }
            Map<String, Map<String, Map<String, Object>>> targetTimeSeriesMap = ConvertUtil.convertResultListToTimeSeries(Lists.newArrayList(targetResult),
                    groupLabels, timeGranLabel);
            int actualSeriesNum = Math.min(maxSeriesNum, targetTimeSeriesMap.size());
            if (actualSeriesNum > 0) {
                Map<String, Map<String, Map<String, Object>>> limitTimeSeriesMap = Maps.newLinkedHashMap();
                int i = 1;
                for (Map.Entry<String, Map<String, Map<String, Object>>> entry : targetTimeSeriesMap.entrySet()) {
                    limitTimeSeriesMap.put(entry.getKey(), entry.getValue());
                    if (++i > actualSeriesNum) {
                        break;
                    }
                }
                targetTimeSeriesMap.clear();
                targetTimeSeriesMap.putAll(limitTimeSeriesMap);
            }
            if (StringUtil.isNotEmpty(dateRangeList)) {
                for (String key : targetTimeSeriesMap.keySet()) {
                    targetTimeSeriesMap.put(key, ConvertUtil.completeTimeseries((TreeMap<String, Map<String, Object>>) targetTimeSeriesMap.get(key), dateRangeList, fill.toString()));
                }
            }
            targetResult = ConvertUtil.convertTimeSeriesToList(targetTimeSeriesMap, groupLabels, timeGranLabel);
        } catch (Exception e) {
            log.error("Parser function : TIME_FLOOR_WITH_FILL error :{} ", e.toString());
            return results;
        } finally {
            if (StringUtil.isNotBlank(sqlQuerySource.getLimit())
                    && !sqlQuerySource.getLimit().equalsIgnoreCase(SQLHelper.INVALID_LIMIT_DESC)
                    && targetResult.size() > Integer.parseInt(sqlQuerySource.getLimit())) {
                targetResult = targetResult.subList(0, Integer.parseInt(sqlQuerySource.getLimit()));
            }
        }
        sortByTime(sqlQuerySource, targetResult, timeGranLabel);
        List<String> objects = Lists.newArrayList();
        objects.add(timeGranLabel);
        objects.addAll(groupLabels);
        unifiedDataType((List<Map<String, String>>) baseResult.getMeta(), targetResult, objects);
        return targetResult;
    }

    private void unifiedDataType(List<Map<String, String>> meta, List<Map<String, Object>> targetResult, List<String> convertKey) {
        Map<String, String> keyType = Maps.newHashMap();
        for (Map<String, String> item : meta) {
            String key = item.get("name");
            if (convertKey.contains(key) && !DataTypeMapping.STRING.equals(item.get("type"))) {
                keyType.put(key, item.get("type"));
            }
        }
        if (keyType.isEmpty()) {
            return;
        }
        targetResult.forEach(x -> {
            for (String key : x.keySet()) {
                if (DataTypeMapping.STRING.equals(keyType.get(key))) {
                    continue;
                }
                if (DataTypeMapping.INT.equals(keyType.get(key))) {
                    x.put(key, Integer.parseInt(String.valueOf(x.get(key))));
                } else if (DataTypeMapping.LONG.equals(keyType.get(key))) {
                    x.put(key, Long.parseLong(String.valueOf(x.get(key))));
                } else if (DataTypeMapping.DOUBLE.equals(keyType.get(key))) {
                    x.put(key, Double.parseDouble(String.valueOf(x.get(key))));
                } else if (DataTypeMapping.FLOAT.equals(keyType.get(key))) {
                    x.put(key, Float.parseFloat(String.valueOf(x.get(key))));
                } else if (DataTypeMapping.BOOLEAN.equals(keyType.get(key))) {
                    x.put(key, Boolean.parseBoolean(String.valueOf(x.get(key))));
                }
            }
        });
    }

    private void sortByTime(SQLQuerySource sqlQuerySource, List<Map<String, Object>> targetResult, String timeGranLabel) {
        if(StringUtil.isEmpty(sqlQuerySource.getListOrderElement())){
            return;
        }
        orderBy:
        for (OrderByElement orderByElement : sqlQuerySource.getListOrderElement()) {
            String orderBy = String.valueOf(orderByElement.getExpression());
            if (orderBy.equals(timeGranLabel)) {
                if (!orderByElement.isAsc()) {
                    Collections.reverse(targetResult);
                    break;
                }
            }
            for (String key : sqlQuerySource.getAliasFields().keySet()) {
                if (sqlQuerySource.getAliasFields().get(key).equalsIgnoreCase(orderBy)) {
                    if (!orderByElement.isAsc()) {
                        Collections.reverse(targetResult);
                        break orderBy;
                    }
                }
            }
        }
    }

    private List<Map<String, Object>> fillResultBasedOnTimeSeries(List<Object> results, List<String> groupLabels, String timeGranLabel) {
        Map<String, Map<String, Map<String, Object>>> targetTimeSeriesMap = ConvertUtil.convertResultListToTimeSeries(results,
                groupLabels, timeGranLabel);
        List<Date> dateRangeList = Lists.newArrayList();
        for (String key : targetTimeSeriesMap.keySet()) {
            Map<String, Map<String, Object>> stringMapMap = targetTimeSeriesMap.get(key);
            for (String l : stringMapMap.keySet()) {
                Date date = NumberUtil.isNumber(l) ? new Date(NumberUtil.parseLong(l) * 1000) : DateUtils.convertStringToDate(l, DateUtils.YYYY_MM_DD_HH24_MM_SS);
                if (!dateRangeList.contains(date)) {
                    dateRangeList.add(date);
                }
            }
        }
        Collections.sort(dateRangeList);
        if (StringUtil.isNotEmpty(dateRangeList)) {
            for (String key : targetTimeSeriesMap.keySet()) {
                targetTimeSeriesMap.put(key, ConvertUtil.completeTimeseries((TreeMap<String, Map<String, Object>>) targetTimeSeriesMap.get(key), dateRangeList, fill));
            }
        }
        return ConvertUtil.convertTimeSeriesToList(targetTimeSeriesMap, groupLabels, timeGranLabel);
    }


    /**
     * 获取基准时间
     * @param firstElement
     * @param timeGranLabel
     * @return
     */
    private Date getBenchmarkDate(Map<String, Object> firstElement, String timeGranLabel) {
        Date benchmarkDate = null;
        Object benchmarkDateObject = firstElement.get(timeGranLabel);

        if (StringUtil.isNumeric(benchmarkDateObject.toString())) {
            benchmarkDate = new DateTime(Long.valueOf(benchmarkDateObject.toString())*1000).toDate();
        } else {
            benchmarkDate = DateUtils.convertStringToDate(benchmarkDateObject.toString(), DateUtils.YYYY_MM_DD_HH24_MM_SS);
        }


        return benchmarkDate;
    }

    /**
     * 获取where查询时间范围
     * @param sqlQuerySource
     * @return
     */
    private WhereTimeRange getWhereTimeRange(SQLQuerySource sqlQuerySource) {
        String dbType = metadataService.getDBTypeByTableName(sqlQuerySource.getTableName());
        WhereTimeRange whereTimeRange = new WhereTimeRange();
        List<FillParam> outToInnerList = getFillParams(sqlQuerySource);
        for (FillParam fillParam : outToInnerList) {
            parserWhereExpressionForWhereTimeRange(whereTimeRange, fillParam.getWhereExpression(), fillParam.getPartitionKey(), dbType);
            if (StringUtil.isNotEmpty(whereTimeRange.getStart()) || StringUtil.isNotEmpty(whereTimeRange.getEnd())) {
                break;
            }
        }
        return whereTimeRange;
    }

    private List<FillParam> getFillParams(SQLQuerySource sqlQuerySource) {
        List<FillParam> result = Lists.newArrayList();
        SQLQuerySource currentSource = sqlQuerySource;
        boolean isOriginalDataSource = false;
        while (!isOriginalDataSource) {
            FillParam currentParam = new FillParam();
            if (currentSource.getSubSelect() == null) {
                isOriginalDataSource = true;
                currentParam.setPartitionKey(currentSource.getPartitionKey());
            }
            currentParam.setAliasFields(currentSource.getAliasFields());
            currentParam.setWhereExpression(currentSource.getWhereExpression());
            result.add(currentParam);
            if (!isOriginalDataSource) {
                currentSource = currentSource.getSubSqlQuerySources().get(0);
            }
        }
        for (int i = result.size() - 1; i >= 0; i--) {
            if (result.size() - 1 == i) {
                continue;
            }
            FillParam subFillParam = result.get(i + 1);
            Map<String, String> subAliasFields = subFillParam.getAliasFields();
            for (String subKey : subAliasFields.keySet()) {
                if (subAliasFields.get(subKey).contains(subFillParam.getPartitionKey())) {
                    result.get(i).setPartitionKey(subKey);
                    break;
                }
            }
        }
        return result;
    }

    /**
     * 解析where表达式并设置数据的时间范围
     * @param whereTimeRange
     * @param whereExpression
     * @param partitionKey
     * @param dbType
     */
    private void parserWhereExpressionForWhereTimeRange(WhereTimeRange whereTimeRange, Expression whereExpression,
                                                                String partitionKey, String dbType) {
        if (StringUtil.isEmpty(whereExpression) || StringUtil.isBlank(partitionKey)) {
            return;
        }
        if (whereExpression instanceof ComparisonOperator) {
            ComparisonOperator whereOperator = (ComparisonOperator) whereExpression;
            String operator = whereOperator.getStringExpression();
            Expression left = whereOperator.getLeftExpression();
            Expression right = whereOperator.getRightExpression();
            if (partitionKey.equals(left.toString())) {
                String value;
                if (right instanceof StringValue) {
                    value = ((StringValue) right).getValue();
                } else if (right instanceof LongValue) {
                    value = right.toString();
                } else {
                    value = SQLFunctionUtil.getQueryValue(right.toString(), dbType);
                }
                if (operator.contains("<")) {
                    bigValue(whereTimeRange, operator.contains("="), value);
                } else if (operator.contains(">")) {
                    minValue(whereTimeRange, operator.contains("="), value);
                }
            } else if (partitionKey.equals(right.toString())) {
                String value;
                if (right instanceof StringValue) {
                    value = ((StringValue) right).getValue();
                } else if (left instanceof LongValue) {
                    value = left.toString();
                } else {
                    value = SQLFunctionUtil.getQueryValue(left.toString(), dbType);
                }
                if (operator.contains("<")) {
                    minValue(whereTimeRange, operator.contains("="), value);
                } else if (operator.contains(">")) {
                    bigValue(whereTimeRange, operator.contains("="), value);
                }
            }
        } else if (whereExpression instanceof Between) {
            Between whereBetween = (Between) whereExpression;
            Expression leftExpression = whereBetween.getLeftExpression();
            if (partitionKey.equals(leftExpression.toString())) {
                if (StringUtil.isNotEmpty(whereTimeRange.getStart())) {
                    String old = whereTimeRange.getStart();
                    String latest = getStrFormatDate(whereBetween.getBetweenExpressionStart().toString());
                    int compare = DateUtil.compare(DateUtils.convertStringToDate(old, DateUtils.YYYY_MM_DD_HH24_MM_SS), DateUtils.convertStringToDate(latest, DateUtils.YYYY_MM_DD_HH24_MM_SS));
                    if (compare < 0) {
                        whereTimeRange.setStart(latest);
                    }
                } else {
                    whereTimeRange.setStart(whereBetween.getBetweenExpressionStart().toString());
                    whereTimeRange.setGreaterThanEquals(true);
                }
                if (StringUtil.isNotEmpty(whereTimeRange.getEnd())) {
                    String old = whereTimeRange.getEnd();
                    String latest = getStrFormatDate(whereBetween.getBetweenExpressionEnd().toString());
                    int compare = DateUtil.compare(DateUtils.convertStringToDate(old, DateUtils.YYYY_MM_DD_HH24_MM_SS), DateUtils.convertStringToDate(latest, DateUtils.YYYY_MM_DD_HH24_MM_SS));
                    if (compare > 0) {
                        whereTimeRange.setEnd(latest);
                    }
                } else {
                    whereTimeRange.setEnd(whereBetween.getBetweenExpressionEnd().toString());
                    whereTimeRange.setMinorThanEquals(true);
                }
            }
        } else if (whereExpression instanceof AndExpression) {
            AndExpression whereAnd = (AndExpression) whereExpression;
            parserWhereExpressionForWhereTimeRange(whereTimeRange, whereAnd.getLeftExpression(), partitionKey, dbType);
            parserWhereExpressionForWhereTimeRange(whereTimeRange, whereAnd.getRightExpression(), partitionKey, dbType);
        } else if (whereExpression instanceof OrExpression) {
            OrExpression whereOr = (OrExpression) whereExpression;
            parserWhereExpressionForWhereTimeRange(whereTimeRange, whereOr.getLeftExpression(), partitionKey, dbType);
            parserWhereExpressionForWhereTimeRange(whereTimeRange, whereOr.getRightExpression(), partitionKey, dbType);
        } else if (whereExpression instanceof Parenthesis) {
            Parenthesis whereExpressionParenthesis = (Parenthesis) whereExpression;
            Expression expression = whereExpressionParenthesis.getExpression();
            parserWhereExpressionForWhereTimeRange(whereTimeRange, expression, partitionKey, dbType);
        }
    }

    private void bigValue(WhereTimeRange whereTimeRange, boolean isClosedInterval, String query) {
        if (StringUtil.isEmpty(whereTimeRange.getEnd())) {
            whereTimeRange.setEnd(query);
            whereTimeRange.setMinorThanEquals(isClosedInterval);
        } else {
            String old = whereTimeRange.getEnd();
            String latest = getStrFormatDate(query);
            int compare = DateUtil.compare(DateUtils.convertStringToDate(old, DateUtils.YYYY_MM_DD_HH24_MM_SS), DateUtils.convertStringToDate(latest, DateUtils.YYYY_MM_DD_HH24_MM_SS));
            if (compare > 0) {
                whereTimeRange.setEnd(latest);
                whereTimeRange.setMinorThanEquals(isClosedInterval);
            }
            if (compare == 0) {
                whereTimeRange.setMinorThanEquals(isClosedInterval && whereTimeRange.isMinorThanEquals());
            }
        }
    }

    private void minValue(WhereTimeRange whereTimeRange, boolean isClosedInterval, String query) {
        if (StringUtil.isEmpty(whereTimeRange.getStart())) {
            whereTimeRange.setStart(query);
            whereTimeRange.setGreaterThanEquals(isClosedInterval);
        } else {
            String old = whereTimeRange.getStart();
            String latest = getStrFormatDate(query);
            int compare = DateUtil.compare(DateUtils.convertStringToDate(old, DateUtils.YYYY_MM_DD_HH24_MM_SS), DateUtils.convertStringToDate(latest, DateUtils.YYYY_MM_DD_HH24_MM_SS));
            if (compare < 0) {
                whereTimeRange.setStart(latest);
                whereTimeRange.setGreaterThanEquals(isClosedInterval);
            }
            if (compare == 0) {
                whereTimeRange.setGreaterThanEquals(isClosedInterval && whereTimeRange.isGreaterThanEquals());
            }
        }
    }

    private String getStrFormatDate(String str) {
        if (NumberUtil.isNumber(str)) {
            return DateUtils.convertTimestampToString(NumberUtil.parseLong(str), DateUtils.YYYY_MM_DD_HH24_MM_SS);
        }
        return str;
    }

    @Data
    private class WhereTimeRange {
        private String start;
        private boolean greaterThanEquals;
        private String end;
        private boolean minorThanEquals;

        public String getStart() {
            if (NumberUtil.isNumber(this.start)) {
                return DateUtils.convertTimestampToString(NumberUtil.parseLong(start), DateUtils.YYYY_MM_DD_HH24_MM_SS);
            }
            return start;
        }

        public String getEnd() {
            if (NumberUtil.isNumber(this.end)) {
                return DateUtils.convertTimestampToString(NumberUtil.parseLong(end), DateUtils.YYYY_MM_DD_HH24_MM_SS);
            }
            return end;
        }
    }

    @Data
    @NoArgsConstructor
    private class FillParam {
        private Map<String, String> aliasFields;
        private Expression whereExpression;
        private String partitionKey;
    }
}
