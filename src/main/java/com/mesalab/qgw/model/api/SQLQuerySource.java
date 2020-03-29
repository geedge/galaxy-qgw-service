package com.mesalab.qgw.model.api;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mesalab.qgw.model.api.udf.UDF;
import com.zdjizhi.utils.StringUtil;
import lombok.Data;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.Between;
import net.sf.jsqlparser.expression.operators.relational.ComparisonOperator;
import net.sf.jsqlparser.statement.select.OrderByElement;

import java.util.List;
import java.util.Map;

/**
 * SQL Parser properties
 */
@Data
public class SQLQuerySource {
    private String tableName;
    private String partitionKey;/** 分区键，多用于检索查询首要过滤列；例如timestamp **/
    private String fromItem;
    private String selectItems;
    private String expr;
    private String groupByClause;
    private String orderBy;
    private String limit;
    private Expression expression; /** where 对象 **/
    private List<OrderByElement> listOrderElement;
    private Map<String, String> fields = Maps.newHashMap();
    private List<UDF> udfList = Lists.newArrayList();
    private Map<String, Object> groupDimension = Maps.newHashMap();
    private PartitionRange partitionRange;
    @Data
    public class PartitionRange {
        private String left;
        private String right;
        private String equal;

    }



    public PartitionRange getPartitionRange() {

        parserExpressionForPartitionRange(expression, partitionKey);

      return partitionRange;
    }




    private void parserExpressionForPartitionRange(Expression expression, String partitionKey) {
        if (StringUtil.isEmpty(expression) || StringUtil.isBlank(partitionKey)) {
            return;
        }
        partitionRange = new PartitionRange();
        if (expression instanceof ComparisonOperator) {
            ComparisonOperator whereOperator = (ComparisonOperator) expression;
            String operator = whereOperator.getStringExpression();
            String left = whereOperator.getLeftExpression().toString();
            String right = whereOperator.getRightExpression().toString();
            if (partitionKey.equals(left)) {
                if (operator.contains("<")) {
                    partitionRange.setRight(right);
                } else if (operator.contains(">")) {
                    partitionRange.setLeft(right);
                } else if (operator.equals("=")) {
                    partitionRange.setEqual(right);
                }
            } else if (partitionKey.equals(right)) {
                if (operator.contains("<")) {
                    partitionRange.setRight(left);
                } else if (operator.contains(">")) {
                    partitionRange.setLeft(left);
                } else if (operator.equals("=")) {
                    partitionRange.setEqual(left);
                }
            }
        } else if (expression instanceof Between) {
            Between whereBetween = (Between) expression;
            Expression leftExpression = whereBetween.getLeftExpression();
            if (partitionKey.equals(leftExpression.toString())) {
                partitionRange.setLeft(whereBetween.getBetweenExpressionStart().toString());
                partitionRange.setRight(whereBetween.getBetweenExpressionEnd().toString());
            }
        } else if (expression instanceof AndExpression) {
            AndExpression whereAnd = (AndExpression) expression;
            parserExpressionForPartitionRange(whereAnd.getLeftExpression(), partitionKey);
            parserExpressionForPartitionRange(whereAnd.getRightExpression(), partitionKey);
        } else if (expression instanceof OrExpression) {
            OrExpression whereOr = (OrExpression) expression;
            parserExpressionForPartitionRange(whereOr.getLeftExpression(), partitionKey);
            parserExpressionForPartitionRange(whereOr.getRightExpression(), partitionKey);
        }
    }











}
