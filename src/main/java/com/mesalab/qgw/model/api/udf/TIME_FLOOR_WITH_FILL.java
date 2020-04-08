package com.mesalab.qgw.model.api.udf;

import com.google.common.base.Joiner;
import com.mesalab.common.enums.BaseResultEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.ConvertUtil;
import com.mesalab.common.utils.SQLFunctionUtil;
import com.mesalab.common.utils.SpringContextUtil;
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
        List<Map<String, String>>  targetResult = null;
        String groupLabel= null;
        String timeGranLabel = null;



        try {

            Map<String, String> groupDimension = sqlQuerySource.getGroupDimension();

            for (String groupKey: groupDimension.keySet()) {
                if (groupDimension.get(groupKey).contains(name)) {
                    timeGranLabel = groupKey;
                } else {
                    groupLabel = Joiner.on(ConvertUtil.GROUP_SEPRATOR).skipNulls().join(groupKey, groupLabel);
                }

            }



            Map<String, Map<String, Map<String, String>>> targetTimeseriesMap = ConvertUtil.convertResultListToTimeseries(results,
                    groupLabel, timeGranLabel);


            List<Date> dateRangeList = null;
            PartitionTimeRange partitionTimeRange = getPartitionTimeRange(sqlQuerySource);

            if (StringUtil.isNotEmpty(partitionTimeRange)) {

                if (StringUtil.isNotEmpty(partitionTimeRange.getLeft()) && StringUtil.isNotEmpty(partitionTimeRange.getRight()) ) {
                    dateRangeList = DateUtils.getDateRange(DateUtils.convertStringToDate((partitionTimeRange.getLeft()), DateUtils.YYYY_MM_DD_HH24_MM_SS),
                            DateUtils.convertStringToDate( (partitionTimeRange.getRight()), DateUtils.YYYY_MM_DD_HH24_MM_SS), period.toString());

                } else if (StringUtil.isNotEmpty(partitionTimeRange.getLeft()) && StringUtil.isEmpty(partitionTimeRange.getRight())) {

                    dateRangeList = DateUtils.getDateRange(DateUtils.convertStringToDate((partitionTimeRange.getLeft()), DateUtils.YYYY_MM_DD_HH24_MM_SS),
                            DateUtils.convertStringToDate(DateUtils.getCurrentDate(DateUtils.YYYY_MM_DD_HH24_MM_SS), DateUtils.YYYY_MM_DD_HH24_MM_SS), period.toString());

                }


            }


            for (String key : targetTimeseriesMap.keySet()) {
                TreeMap<String, Map<String, String>> seriesMap = (TreeMap <String, Map<String, String>>) targetTimeseriesMap.get(key);

                if(StringUtil.isEmpty(dateRangeList)){ //尝试补全中间结果
                    dateRangeList = DateUtils.getDateRange(DateUtils.convertStringToDate(seriesMap.firstKey(), DateUtils.YYYY_MM_DD_HH24_MM_SS),
                            DateUtils.convertStringToDate(seriesMap.lastKey(), DateUtils.YYYY_MM_DD_HH24_MM_SS), period.toString());
                }

                targetTimeseriesMap.put(key,ConvertUtil.completeTimeseries(seriesMap, dateRangeList, fill.toString()));

            }
            targetResult =  ConvertUtil.convertTimeseriesToList(targetTimeseriesMap, groupLabel, timeGranLabel);
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
            throw new BusinessException(BaseResultEnum.FAIL.getCode(), "解析where表达式获取partitionRange异常", e);
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
                String query = right.toString();
                if (!(right instanceof StringValue || right instanceof LongValue)) {
                    query = SQLFunctionUtil.getQueryValue(query, dbType);
                }
                if (operator.contains("<")) {
                    partitionTimeRange.setRight(query);
                } else if (operator.contains(">")) {
                    partitionTimeRange.setLeft(query);
                } else if (operator.equals("=")) {
                    partitionTimeRange.setEqual(query);
                }
            } else if (partitionKey.equals(right.toString())) {
                String query = left.toString();
                if (!(left instanceof StringValue || left instanceof LongValue)) {
                    query = SQLFunctionUtil.getQueryValue(query, dbType);
                }
                if (operator.contains("<")) {
                    partitionTimeRange.setRight(query);
                } else if (operator.contains(">")) {
                    partitionTimeRange.setLeft(query);
                } else if (operator.equals("=")) {
                    partitionTimeRange.setEqual(query);
                }
            }
        } else if (whereExpression instanceof Between) {
            Between whereBetween = (Between) whereExpression;
            Expression leftExpression = whereBetween.getLeftExpression();
            if (partitionKey.equals(leftExpression.toString())) {
                partitionTimeRange.setLeft(whereBetween.getBetweenExpressionStart().toString());
                partitionTimeRange.setRight(whereBetween.getBetweenExpressionEnd().toString());
            }
        } else if (whereExpression instanceof AndExpression) {
            AndExpression whereAnd = (AndExpression) whereExpression;
            parserWhereExpressionForPartitionRange(partitionTimeRange, whereAnd.getLeftExpression(), partitionKey, dbType);
            parserWhereExpressionForPartitionRange(partitionTimeRange, whereAnd.getRightExpression(), partitionKey, dbType);
        } else if (whereExpression instanceof OrExpression) {
            OrExpression whereOr = (OrExpression) whereExpression;
            parserWhereExpressionForPartitionRange(partitionTimeRange, whereOr.getLeftExpression(), partitionKey, dbType);
            parserWhereExpressionForPartitionRange(partitionTimeRange, whereOr.getRightExpression(), partitionKey, dbType);
        }
    }

    @Data
    private class PartitionTimeRange {
        private String left;
        private String right;
        private String equal;

    }
}
