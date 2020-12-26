package com.mesalab.qgw.model.api.udf;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.ConvertUtil;
import com.mesalab.common.utils.SQLFunctionUtil;
import com.mesalab.common.utils.SpringContextUtil;
import com.mesalab.qgw.model.api.EngineConfigSource;
import com.mesalab.qgw.model.api.SQLQuerySource;
import com.mesalab.qgw.service.MetadataService;
import com.zdjizhi.utils.DateUtils;
import com.zdjizhi.utils.StringUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Data
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
        List<Map<String, Object>>  targetResult = null;
        String groupLabel= null;
        String timeGranLabel = null;

        try {
            Map<String, String> groupDimension = sqlQuerySource.getGroupDimension();
            if (results.isEmpty() || StringUtil.isBlank(fill) || StringUtil.isEmpty(groupDimension)) {
                return results;
            }
            for (String groupKey: groupDimension.keySet()) {
                if (groupDimension.get(groupKey).contains(name) &&
                        groupDimension.get(groupKey).contains(timestamp)) {
                    timeGranLabel = groupKey;
                } else {
                    groupLabel = Joiner.on(ConvertUtil.GROUP_SEPRATOR).skipNulls().join(groupKey, groupLabel);
                }

            }

            WhereTimeRange whereTimeRange = getWhereTimeRange(sqlQuerySource);
            Date benchmarkDate = getBenchmarkDate((Map<String,Object>) results.get(0), timeGranLabel);


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
                log.warn("SQL中时间条件不完整[startTime:{} - endTime:{}],补全功能无效!", whereTimeRange.getStart(), whereTimeRange.getEnd());
                return results;
            }

            if (StringUtil.isNotEmpty(dateRangeList) &&
                    dateRangeList.size() >= 2 ) {
                dateRangeList = dateRangeList.subList(1, dateRangeList.size() - 1);
            } else {
                return results;
            }


            int  maxSeriesNum =  engineConfigSource.getMaxCacheNum() / dateRangeList.size();
            if (maxSeriesNum == 0) {
                log.warn("初始时间范围超出系统设置，无法进行补全[getMaxCacheNum:{} - dateRangeNum:{}],补全功能无效!", engineConfigSource.getMaxCacheNum(), dateRangeList.size());
                return results;
            }


            Map<String, Map<String, Map<String, Object>>> targetTimeseriesMap =  ConvertUtil.convertResultListToTimeseries(results,
                    groupLabel, timeGranLabel);

            int actualSeriesNum = maxSeriesNum > targetTimeseriesMap.size() ? targetTimeseriesMap.size() : maxSeriesNum;

            if (actualSeriesNum > 0) {

                Map<String, Map<String, Map<String, Object>>> limitTimeseriesMap = Maps.newLinkedHashMap();
                int i = 1;
                for (Map.Entry<String, Map<String, Map<String, Object>>> entry: targetTimeseriesMap.entrySet()) {
                    limitTimeseriesMap.put(entry.getKey(), entry.getValue());
                    if (++i > actualSeriesNum) {
                        break;
                    }

                }
                targetTimeseriesMap.clear();
                targetTimeseriesMap.putAll(limitTimeseriesMap);
            }

            if(StringUtil.isNotEmpty(dateRangeList)){

                for (String key : targetTimeseriesMap.keySet()) {
                    targetTimeseriesMap.put(key,ConvertUtil.completeTimeseries((TreeMap<String, Map<String, Object>>) targetTimeseriesMap.get(key), dateRangeList, fill.toString()));
                }
            }

            targetResult = ConvertUtil.convertTimeseriesToList(targetTimeseriesMap, groupLabel, timeGranLabel);

        } catch (Exception e) {
            log.error("Parser function : TIME_FLOOR_WITH_FILL error :{} ", e.toString());
        }

        return targetResult;
    }


    /**
     * 获取基准时间
     * @param firstElement
     * @param timeGranLabel
     * @return
     */
    private Date getBenchmarkDate(Map<String, Object> firstElement, String timeGranLabel) {
        Date benchmarkDate = null;
        try {
            Object benchmarkDateObject = firstElement.get(timeGranLabel);

            if (StringUtil.isNumeric(benchmarkDateObject.toString())) {
                benchmarkDate = new DateTime(Long.valueOf(benchmarkDateObject.toString())*1000).toDate();
            } else {
                benchmarkDate = DateUtils.convertStringToDate(benchmarkDateObject.toString(), DateUtils.YYYY_MM_DD_HH24_MM_SS);
            }
        } catch (Exception ex) {
            log.error("Get BenchmarkDate Error ", ex);

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
        try {
            parserWhereExpressionForWhereTimeRange(whereTimeRange, whereExpression, partitionKey, dbType);
        } catch (Exception e) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(),
                    ResultCodeEnum.SQL_SYNTAX_ERROR.getCode(), "解析where表达式，获取数据的时间范围异常", e);
        }
        return whereTimeRange;
    }

    /**
     * 解析where表达式并设置数据的时间范围
     * @param whereTimeRange
     * @param whereExpression
     * @param partitionKey
     * @param dbType
     */
    private void parserWhereExpressionForWhereTimeRange(WhereTimeRange whereTimeRange, Expression whereExpression, String partitionKey, String dbType) {
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
                } else if (operator.contains(">")) {
                    whereTimeRange.setStart(query);
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
                } else if (operator.contains(">")) {
                    whereTimeRange.setEnd(query);
                }
            }
        } else if (whereExpression instanceof Between) {
            Between whereBetween = (Between) whereExpression;
            Expression leftExpression = whereBetween.getLeftExpression();
            if (partitionKey.equals(leftExpression.toString())) {
                whereTimeRange.setStart(whereBetween.getBetweenExpressionStart().toString());
                whereTimeRange.setEnd(whereBetween.getBetweenExpressionEnd().toString());
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


    private class WhereTimeRange {
        private String start;
        private String end;

        public String getStart() {
            return start;
        }

        public void setStart(String start) {
            this.start = start;
        }

        public String getEnd() {
            return end;
        }

        public void setEnd(String end) {
            this.end = end;
        }
    }


}
