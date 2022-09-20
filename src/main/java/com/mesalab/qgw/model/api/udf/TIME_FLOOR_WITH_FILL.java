package com.mesalab.qgw.model.api.udf;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mesalab.common.utils.ConvertUtil;
import com.mesalab.common.utils.sqlparser.SQLFunctionUtil;
import com.mesalab.common.utils.SpringContextUtil;
import com.mesalab.common.utils.sqlparser.SQLHelper;
import com.mesalab.qgw.model.api.EngineConfigSource;
import com.mesalab.qgw.model.api.SQLQuerySource;
import com.mesalab.qgw.service.MetadataService;
import com.zdjizhi.utils.DateUtils;
import com.zdjizhi.utils.StringUtil;
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
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

@AllArgsConstructor
@NoArgsConstructor
public class TIME_FLOOR_WITH_FILL implements UDF{
    private static final Log log = LogFactory.get();
    @Autowired
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
    public Object execute(SQLQuerySource sqlQuerySource, List<Object> results) {
        List<Map<String, Object>> targetResult = Lists.newArrayList();
        List<String> groupLabels = Lists.newArrayList();
        String timeGranLabel = null;
        try {
            Map<String, String> groupDimension = sqlQuerySource.getGroupDimension();
            if (results.isEmpty() || StringUtil.isBlank(fill) || StringUtil.isEmpty(groupDimension)) {
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
            Date benchmarkDate = getBenchmarkDate((Map<String, Object>) results.get(0), timeGranLabel);

            targetResult = fillResultBasedOnTimeSeries(results, groupLabels, timeGranLabel);

            List<Date> dateRangeList = null;

            if (StringUtil.isNotBlank(whereTimeRange.getStart())) {

                if (StringUtil.isBlank(whereTimeRange.getEnd())) {
                    whereTimeRange.setEnd(DateUtils.getCurrentDate(DateUtils.YYYY_MM_DD_HH24_MM_SS));
                }

                List<Date> leftDateRange = DateUtils.getLeftDateRange(benchmarkDate,
                        DateUtils.convertStringToDate((whereTimeRange.getStart()), DateUtils.YYYY_MM_DD_HH24_MM_SS), period);
                List<Date> rightDateRange = DateUtils.getRightDateRange(benchmarkDate,
                        DateUtils.convertStringToDate((whereTimeRange.getEnd()), DateUtils.YYYY_MM_DD_HH24_MM_SS), period);

                Collections.reverse(leftDateRange);
                dateRangeList = Lists.newArrayList(Iterables.concat(leftDateRange, rightDateRange));

            } else {
                log.warn("The time range is empty [startTime:{},endTime:{}], so the time series data cannot be filled.", whereTimeRange.getStart(), whereTimeRange.getEnd());
                return targetResult;
            }
            if (StringUtil.isNotEmpty(dateRangeList)
                    && !whereTimeRange.isMinorThanEquals()
                    && DateUtil.toInstant(DateUtils.convertStringToDate((whereTimeRange.getEnd()), DateUtils.YYYY_MM_DD_HH24_MM_SS))
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
        return targetResult;
    }

    private List<Map<String, Object>> fillResultBasedOnTimeSeries(List<Object> results, List<String> groupLabels, String timeGranLabel) {
        Map<String, Map<String, Map<String, Object>>> targetTimeSeriesMap = ConvertUtil.convertResultListToTimeSeries(results,
                groupLabels, timeGranLabel);
        List<Date> dateRangeList = Lists.newArrayList();
        for (String key : targetTimeSeriesMap.keySet()) {
            Map<String, Map<String, Object>> stringMapMap = targetTimeSeriesMap.get(key);
            for (String l : stringMapMap.keySet()) {
                Date date = new Date(Long.parseLong(l) * 1000);
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
        String partitionKey = sqlQuerySource.getPartitionKey();
        Expression whereExpression = sqlQuerySource.getWhereExpression();
        String dbType = metadataService.getDBTypeByTableName(sqlQuerySource.getTableName());
        WhereTimeRange whereTimeRange = new WhereTimeRange();
        parserWhereExpressionForWhereTimeRange(whereTimeRange, whereExpression, partitionKey, dbType);

        return whereTimeRange;
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
                String query;
                if (right instanceof StringValue) {
                    query = ((StringValue) right).getValue();
                } else if (right instanceof LongValue) {
                    query = right.toString();
                } else {
                    query = SQLFunctionUtil.getQueryValue(right.toString(), dbType);
                }
                if (operator.contains("<")) {
                    whereTimeRange.setEnd(query);
                    whereTimeRange.setMinorThanEquals(operator.contains("="));
                } else if (operator.contains(">")) {
                    whereTimeRange.setStart(query);
                    whereTimeRange.setGreaterThanEquals(operator.contains("="));
                }
            } else if (partitionKey.equals(right.toString())) {
                String query;
                if (left instanceof StringValue) {
                    query = ((StringValue) right).getValue();
                } else if (left instanceof LongValue) {
                    query = left.toString();
                } else {
                    query = SQLFunctionUtil.getQueryValue(left.toString(), dbType);
                }
                if (operator.contains("<")) {
                    whereTimeRange.setStart(query);
                    whereTimeRange.setGreaterThanEquals(operator.contains("="));
                } else if (operator.contains(">")) {
                    whereTimeRange.setEnd(query);
                    whereTimeRange.setMinorThanEquals(operator.contains("="));
                }
            }
        } else if (whereExpression instanceof Between) {
            Between whereBetween = (Between) whereExpression;
            Expression leftExpression = whereBetween.getLeftExpression();
            if (partitionKey.equals(leftExpression.toString())) {
                whereTimeRange.setStart(whereBetween.getBetweenExpressionStart().toString());
                whereTimeRange.setGreaterThanEquals(true);
                whereTimeRange.setEnd(whereBetween.getBetweenExpressionEnd().toString());
                whereTimeRange.setMinorThanEquals(true);
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


}
