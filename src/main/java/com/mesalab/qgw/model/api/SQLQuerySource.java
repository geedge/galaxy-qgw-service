package com.mesalab.qgw.model.api;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mesalab.qgw.model.api.udf.UDF;
import com.zdjizhi.utils.StringUtil;
import lombok.Data;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
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
    private Map<String, String> aliasFields= Maps.newHashMap();
    private List<UDF> udfList = Lists.newArrayList();
    private Map<String, String> groupDimension = Maps.newHashMap();
    private PartitionTimeRange partitionTimeRange;
    @Data
    public class PartitionTimeRange {
        private Expression left;
        private Expression right;
        private Expression equal;

    }



    public PartitionTimeRange getPartitionTimeRange() {
        partitionTimeRange = new PartitionTimeRange();
        parserExpressionForPartitionRange(expression, partitionKey);

      return partitionTimeRange;
    }




    private void parserExpressionForPartitionRange(Expression expression, String partitionKey) {
        if (StringUtil.isEmpty(expression) || StringUtil.isBlank(partitionKey)) {
            return;
        }
        if (expression instanceof ComparisonOperator) {
            ComparisonOperator whereOperator = (ComparisonOperator) expression;
            String operator = whereOperator.getStringExpression();
            Expression left = whereOperator.getLeftExpression();
            Expression right = whereOperator.getRightExpression();
            if (partitionKey.equals(left.toString())) {
                right = getActualValue(right);
                if (operator.contains("<")) {
                    partitionTimeRange.setRight(right);
                } else if (operator.contains(">")) {
                    partitionTimeRange.setLeft(right);
                } else if (operator.equals("=")) {
                    partitionTimeRange.setEqual(right);
                }
            } else if (partitionKey.equals(right.toString())) {
                left = getActualValue(left);
                if (operator.contains("<")) {
                    partitionTimeRange.setRight(left);
                } else if (operator.contains(">")) {
                    partitionTimeRange.setLeft(left);
                } else if (operator.equals("=")) {
                    partitionTimeRange.setEqual(left);
                }
            }
        } else if (expression instanceof Between) {
            Between whereBetween = (Between) expression;
            Expression leftExpression = whereBetween.getLeftExpression();
            if (partitionKey.equals(leftExpression.toString())) {
                partitionTimeRange.setLeft(whereBetween.getBetweenExpressionStart());
                partitionTimeRange.setRight(whereBetween.getBetweenExpressionEnd());
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

    /**
     * 伪代码：后期需要在相应的dialect中获取；以及根据函数实际值
     * @param expr
     * @return
     */
    private Expression getActualValue(Expression expr) {
        if (expr == null) {
            return null;
        }
        if (expr instanceof Function) {
            Function fun = (Function) expr;
            if (fun.getName().equalsIgnoreCase("toDateTime")) {
                Expression expression = fun.getParameters().getExpressions().get(0);
                return expression;
            } else if (fun.getName().contains("toStartOfDay")) {
                return fun.getParameters().getExpressions().get(0);
            }
        }
        return expr;
    }










}
