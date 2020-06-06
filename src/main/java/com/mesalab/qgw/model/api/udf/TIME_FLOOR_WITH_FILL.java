package com.mesalab.qgw.model.api.udf;

import com.google.common.base.Joiner;
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
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

@Slf4j
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TIME_FLOOR_WITH_FILL implements UDF{

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
            if(StringUtil.isBlank(fill) || StringUtil.isEmpty(groupDimension)) { //补全函数为空或列无时间序列key时，直接返回
                return results;
            }

            PartitionTimeRange partitionTimeRange = getPartitionTimeRange(sqlQuerySource);
            List<Date> dateRangeList = null;
            if (StringUtil.isNotEmpty(partitionTimeRange.getStart()) && StringUtil.isNotEmpty(partitionTimeRange.getEnd()) ) {
                dateRangeList = DateUtils.getDateRange(DateUtils.convertStringToDate((partitionTimeRange.getStart()), DateUtils.YYYY_MM_DD_HH24_MM_SS),
                        DateUtils.convertStringToDate( (partitionTimeRange.getEnd()), DateUtils.YYYY_MM_DD_HH24_MM_SS), period.toString());

            } else if (StringUtil.isNotEmpty(partitionTimeRange.getStart()) && StringUtil.isEmpty(partitionTimeRange.getEnd())) {

                dateRangeList = DateUtils.getDateRange(DateUtils.convertStringToDate((partitionTimeRange.getStart()), DateUtils.YYYY_MM_DD_HH24_MM_SS),
                        DateUtils.convertStringToDate(DateUtils.getCurrentDate(DateUtils.YYYY_MM_DD_HH24_MM_SS), DateUtils.YYYY_MM_DD_HH24_MM_SS), period.toString());

            } else {
                log.warn("SQL中时间条件不完整[startTime:{} - endTime:{}],补全功能无效!", partitionTimeRange.getStart(), partitionTimeRange.getEnd());
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
            log.error("Parser function : TIME_FLOOR_WITH_FILL error : {} ", e);
        }

        return targetResult;
    }

    /**
     * 获取partitionRange
     * @param sqlQuerySource
     * @return
     */
    private PartitionTimeRange getPartitionTimeRange(SQLQuerySource sqlQuerySource) {
        String partitionKey = sqlQuerySource.getPartitionKey();
        Expression whereExpression = sqlQuerySource.getWhereExpression();
        String dbType = metadataService.getDBTypeByTableName(sqlQuerySource.getTableName());
        PartitionTimeRange partitionTimeRange = new PartitionTimeRange();
        try {
            parserWhereExpressionForPartitionRange(partitionTimeRange, whereExpression, partitionKey, dbType);
        } catch (Exception e) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(),
                    ResultCodeEnum.SQL_SYNTAX_ERROR.getCode(), "解析where表达式获取partitionRange异常", e);
        }
        return partitionTimeRange;
    }

    /**
     * 解析where表达式并设置partitionRange
     * @param partitionTimeRange
     * @param whereExpression
     * @param partitionKey
     * @param dbType
     */
    private void parserWhereExpressionForPartitionRange(PartitionTimeRange partitionTimeRange, Expression whereExpression, String partitionKey, String dbType) {
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
                    partitionTimeRange.setEnd(query);
                } else if (operator.contains(">")) {
                    partitionTimeRange.setStart(query);
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
                    partitionTimeRange.setStart(query);
                } else if (operator.contains(">")) {
                    partitionTimeRange.setEnd(query);
                }
            }
        } else if (whereExpression instanceof Between) {
            Between whereBetween = (Between) whereExpression;
            Expression leftExpression = whereBetween.getLeftExpression();
            if (partitionKey.equals(leftExpression.toString())) {
                partitionTimeRange.setStart(whereBetween.getBetweenExpressionStart().toString());
                partitionTimeRange.setEnd(whereBetween.getBetweenExpressionEnd().toString());
            }
        } else if (whereExpression instanceof AndExpression) {
            AndExpression whereAnd = (AndExpression) whereExpression;
            parserWhereExpressionForPartitionRange(partitionTimeRange, whereAnd.getLeftExpression(), partitionKey, dbType);
            parserWhereExpressionForPartitionRange(partitionTimeRange, whereAnd.getRightExpression(), partitionKey, dbType);
        } else if (whereExpression instanceof OrExpression) {
            OrExpression whereOr = (OrExpression) whereExpression;
            parserWhereExpressionForPartitionRange(partitionTimeRange, whereOr.getLeftExpression(), partitionKey, dbType);
            parserWhereExpressionForPartitionRange(partitionTimeRange, whereOr.getRightExpression(), partitionKey, dbType);
        } else if (whereExpression instanceof Parenthesis) {
            Parenthesis whereExpressionParenthesis = (Parenthesis) whereExpression;
            Expression expression = whereExpressionParenthesis.getExpression();
            parserWhereExpressionForPartitionRange(partitionTimeRange, expression, partitionKey, dbType);
        }
    }

    @Data
    private class PartitionTimeRange {
        private String start;
        private String end;
    }
}
