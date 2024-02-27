package com.mesalab.common.utils.sqlparser;

import lombok.SneakyThrows;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.arithmetic.Division;
import net.sf.jsqlparser.expression.operators.arithmetic.Multiplication;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.*;

import java.util.*;

/**
 * TODO
 *
 * @Classname FunctionsMergeHelper
 * @Date 2024/2/20 10:42
 * @Author wWei
 */
public class FunctionsMergeHelper {

    private static final String FROM_UNIXTIME = "FROM_UNIXTIME";
    private static final String TIME_FLOOR_WITH_FILL = "TIME_FLOOR_WITH_FILL";
    private static final String UNIX_TIMESTAMP = "UNIX_TIMESTAMP";
    private static final String TIME_FORMAT = "TIME_FORMAT";
    private static final String DATE_TIME_FORMAT_PATTERN = "'yyyy-MM-dd HH:mm:ss'";
    private static final String TIME_FLOOR = "TIME_FLOOR";
    private static final String MILLIS_TO_TIMESTAMP = "MILLIS_TO_TIMESTAMP";
    private static final String TIMESTAMP_TO_MILLIS = "TIMESTAMP_TO_MILLIS";

    private FunctionsMergeHelper() {
    }

    /**
     * Desc: Merge the TIME_FLOOR_WITH_FILL functions in the SQL statement
     * 1. FROM_UNIXTIME(TIME_FLOOR_WITH_FILL(UNIX_TIMESTAMP(column), *[, *])) ==>
     * 1. Select Item: TIME_FORMAT(TIME_FLOOR(column, *), 'yyyy-MM-dd HH:mm:ss')
     * 2. GroupBy or Order By Expr: TIME_FLOOR(column, *)
     * 2. FROM_UNIXTIME(TIME_FLOOR_WITH_FILL(column, *[, *])) ==>
     * 1. Select Item: TIME_FORMAT(TIME_FLOOR(MILLIS_TO_TIMESTAMP(column * 1000), *), 'yyyy-MM-dd HH:mm:ss')
     * 2. GroupBy or Order By Expr: TIME_FLOOR(MILLIS_TO_TIMESTAMP(column * 1000), *)
     * 3. TIME_FLOOR_WITH_FILL(UNIX_TIMESTAMP(column), *[, *]) ==>
     * 1. Select Item: TIMESTAMP_TO_MILLIS(TIME_FLOOR(column, *)) / 1000
     * 2. GroupBy or Order By Expr: TIME_FLOOR(column, *)
     * 4. TIME_FLOOR_WITH_FILL(column, *[, *]) ==>
     * 1. Select Item: TIMESTAMP_TO_MILLIS(TIME_FLOOR(MILLIS_TO_TIMESTAMP(column * 1000), *)) / 1000
     * 2. GroupBy or Order By Expr: TIME_FLOOR(MILLIS_TO_TIMESTAMP(column * 1000), *)
     *
     * @param sql
     * @return {@link String}
     * @created by wWei
     * @date 2024/2/24 14:49
     */
    public static String build(String sql) throws JSQLParserException {
        Select select = (Select) CCJSqlParserUtil.parse(sql);
        select.getSelectBody().accept(new SelectAdapter());
        return select.toString();
    }

    private static class SelectAdapter extends SelectVisitorAdapter {

        @SneakyThrows
        @Override
        public void visit(PlainSelect plainSelect) {

            Map<String, String> aliasExpr = new HashMap<>();
            List<SelectItem> selectItems = plainSelect.getSelectItems();
            if (selectItems != null) {
                for (SelectItem selectItem : selectItems) {
                    SelectItemAdapter selectItemAdapter = new SelectItemAdapter();
                    selectItem.accept(selectItemAdapter);
                    Map<String, String> itemAliasExpr = selectItemAdapter.getAliasExpr();
                    if (!itemAliasExpr.isEmpty()) {
                        aliasExpr.putAll(itemAliasExpr);
                    }
                }
            }

            GroupByElement groupBy = plainSelect.getGroupBy();
            if (groupBy != null && groupBy.getGroupByExpressionList() != null && !groupBy.getGroupByExpressionList().getExpressions().isEmpty()) {
                groupBy.accept(new GroupByAdapter());
            }
            List<OrderByElement> orderByElements = plainSelect.getOrderByElements();
            if (orderByElements != null) {
                for (OrderByElement orderByElement : orderByElements) {
                    if (aliasExpr.containsKey(orderByElement.getExpression().toString())) {
                        Expression expression = CCJSqlParserUtil.parseExpression(aliasExpr.get(orderByElement.getExpression().toString()));
                        orderByElement.setExpression(expression);
                    }
                    orderByElement.accept(new OrderByAdapter());
                }
            }

            FromItem fromItem = plainSelect.getFromItem();
            if (fromItem != null) {
                fromItem.accept(new FromItemAdapter());
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

    private static Function createFunction(String funName, List<Expression> params) {
        Function function = new Function();
        function.setName(funName);
        function.setParameters(new ExpressionList(params));
        return function;
    }

    private static boolean isUnixTimestampFunction(Expression expression) {
        if (!(expression instanceof Function)) {
            return false;
        }
        Function function = (Function) expression;
        return function.getName().equalsIgnoreCase(UNIX_TIMESTAMP)
                && function.getParameters().getExpressions().size() == 1;
    }

    private static boolean isTimeFloorWithFillFunction(Expression expression) {
        if (!(expression instanceof Function)) {
            return false;
        }
        Function function = (Function) expression;
        return TIME_FLOOR_WITH_FILL.equalsIgnoreCase(function.getName())
                && function.getParameters().getExpressions().size() >= 2
                && function.getParameters().getExpressions().size() <= 3;
    }

    private static boolean isFromUnixtimeFunction(Expression expression) {
        if (!(expression instanceof Function)) {
            return false;
        }
        Function function = (Function) expression;
        return function.getName().equalsIgnoreCase(FROM_UNIXTIME)
                && function.getParameters().getExpressions().size() == 1;
    }


    private static class SelectItemAdapter extends SelectItemVisitorAdapter {

        private Map<String, String> aliasExpr = new HashMap<>();

        public Map<String, String> getAliasExpr() {
            return aliasExpr;
        }

        @Override
        public void visit(SelectExpressionItem selectExpressionItem) {
            Expression expression = selectExpressionItem.getExpression();
            if (!(expression instanceof Function)) {
                return;
            }
            Function mainFunction = (Function) expression;
            if (isFromUnixtimeFunction(mainFunction)) {
                Expression fromUnixtimeArg0 = mainFunction.getParameters().getExpressions().get(0);
                if (isTimeFloorWithFillFunction(fromUnixtimeArg0)) {
                    Function timeFloorWithFillFun = (Function) fromUnixtimeArg0;
                    Expression timeFloorWithFillFunArg0 = timeFloorWithFillFun.getParameters().getExpressions().get(0);
                    Expression timeFloorWithFillFunArg1 = timeFloorWithFillFun.getParameters().getExpressions().get(1);
                    if (isUnixTimestampFunction(timeFloorWithFillFunArg0) && ((Function) timeFloorWithFillFunArg0).getParameters().getExpressions().get(0) instanceof Column) {
                        if (selectExpressionItem.getAlias() != null) {
                            aliasExpr.put(selectExpressionItem.getAlias().getName(), mainFunction.toString());
                        }

                        Function unixTimestampFun = (Function) timeFloorWithFillFunArg0;
                        List<Expression> timeFloorArgs = new ArrayList<>(Arrays.asList(unixTimestampFun.getParameters().getExpressions().get(0), timeFloorWithFillFunArg1));
                        Function timeFloor = createFunction(TIME_FLOOR, timeFloorArgs);
                        mainFunction.setName(TIME_FORMAT);
                        List<Expression> timeFormatArgs = new ArrayList<>(Arrays.asList(timeFloor, new StringValue(DATE_TIME_FORMAT_PATTERN)));
                        mainFunction.setParameters(new ExpressionList(timeFormatArgs));
                    } else if (timeFloorWithFillFunArg0 instanceof Column) {
                        if (selectExpressionItem.getAlias() != null) {
                            aliasExpr.put(selectExpressionItem.getAlias().getName(), mainFunction.toString());
                        }
                        Multiplication multiplication = new Multiplication();
                        multiplication.setLeftExpression(timeFloorWithFillFunArg0);
                        multiplication.setRightExpression(new LongValue(1000));

                        List<Expression> millisToTimestampArgs = new ArrayList<>(Collections.singletonList(multiplication));
                        Function millsToTimestamp = createFunction(MILLIS_TO_TIMESTAMP, millisToTimestampArgs);

                        ArrayList<Expression> timeFloorArgs = new ArrayList<>(Arrays.asList(millsToTimestamp, timeFloorWithFillFunArg1));
                        Function timeFloorFun = createFunction(TIME_FLOOR, timeFloorArgs);

                        mainFunction.setName(TIME_FORMAT);
                        List<Expression> timeFormatArgs = new ArrayList<>(Arrays.asList(timeFloorFun, new StringValue(DATE_TIME_FORMAT_PATTERN)));
                        mainFunction.setParameters(new ExpressionList(timeFormatArgs));
                    }
                }
            } else if (isTimeFloorWithFillFunction(mainFunction)) {
                Expression timeFloorWithFillFunArg0 = mainFunction.getParameters().getExpressions().get(0);
                Expression timeFloorWithFillFunArg1 = mainFunction.getParameters().getExpressions().get(1);
                if (isUnixTimestampFunction(timeFloorWithFillFunArg0) && ((Function) timeFloorWithFillFunArg0).getParameters().getExpressions().get(0) instanceof Column) {

                    if (selectExpressionItem.getAlias() != null) {
                        aliasExpr.put(selectExpressionItem.getAlias().getName(), mainFunction.toString());
                    }
                    Function unixTimestampFun = (Function) timeFloorWithFillFunArg0;

                    List<Expression> expressions = new ArrayList<>(Arrays.asList(unixTimestampFun.getParameters().getExpressions().get(0), timeFloorWithFillFunArg1));
                    Function timeFloor = createFunction(TIME_FLOOR, expressions);

                    List<Expression> timestampToMillsArgs = new ArrayList<>(Collections.singletonList(timeFloor));
                    Function timestampToMillis = createFunction(TIMESTAMP_TO_MILLIS, timestampToMillsArgs);

                    Division division = new Division();
                    division.setLeftExpression(timestampToMillis);
                    division.setRightExpression(new LongValue(1000));

                    selectExpressionItem.setExpression(division);
                } else if (timeFloorWithFillFunArg0 instanceof Column) {
                    if (selectExpressionItem.getAlias() != null) {
                        aliasExpr.put(selectExpressionItem.getAlias().getName(), mainFunction.toString());
                    }

                    Multiplication multiplication = new Multiplication();
                    multiplication.setLeftExpression(timeFloorWithFillFunArg0);
                    multiplication.setRightExpression(new LongValue(1000));

                    List<Expression> millisToTimestampArgs = new ArrayList<>(Collections.singletonList(multiplication));
                    Function millsToTimestamp = createFunction(MILLIS_TO_TIMESTAMP, millisToTimestampArgs);

                    List<Expression> timeFloorArgs = new ArrayList<>(Arrays.asList(millsToTimestamp, timeFloorWithFillFunArg1));
                    Function timeFloor = createFunction(TIME_FLOOR, timeFloorArgs);

                    List<Expression> timestampToMillsArgs = new ArrayList<>(Collections.singletonList(timeFloor));
                    Function timestampToMills = createFunction(TIMESTAMP_TO_MILLIS, timestampToMillsArgs);

                    Division division = new Division();
                    division.setLeftExpression(timestampToMills);
                    division.setRightExpression(new LongValue(1000));

                    selectExpressionItem.setExpression(division);
                }
            }
        }

    }

    private static class FromItemAdapter extends FromItemVisitorAdapter {
        @Override
        public void visit(SubSelect subSelect) {
            SelectBody selectBody = subSelect.getSelectBody();
            selectBody.accept(new SelectAdapter());
        }
    }

    private static class GroupByAdapter implements GroupByVisitor {
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
                Function mainFunction = (Function) expression;
                if (isFromUnixtimeFunction(expression)) {
                    Expression fromUnixtimeArg0 = mainFunction.getParameters().getExpressions().get(0);
                    if (isTimeFloorWithFillFunction(fromUnixtimeArg0)) {
                        Function timeFloorWithFillFun = (Function) fromUnixtimeArg0;
                        Expression timeFloorWithFillFunArg0 = timeFloorWithFillFun.getParameters().getExpressions().get(0);
                        Expression timeFloorWithFillFunArg1 = timeFloorWithFillFun.getParameters().getExpressions().get(1);
                        if (isUnixTimestampFunction(timeFloorWithFillFunArg0) && ((Function) timeFloorWithFillFunArg0).getParameters().getExpressions().get(0) instanceof Column) {
                            Function unixTimestampFun = (Function) timeFloorWithFillFunArg0;
                            mainFunction.setName(TIME_FLOOR);
                            List<Expression> timeFloorArgs = new ArrayList<>(Arrays.asList(unixTimestampFun.getParameters().getExpressions().get(0), timeFloorWithFillFunArg1));
                            mainFunction.setParameters(new ExpressionList(timeFloorArgs));
                        } else if (timeFloorWithFillFunArg0 instanceof Column) {
                            Multiplication multiplication = new Multiplication();
                            multiplication.setLeftExpression(timeFloorWithFillFunArg0);
                            multiplication.setRightExpression(new LongValue(1000));

                            List<Expression> millisToTimestampArgs = new ArrayList<>(Collections.singletonList(multiplication));
                            Function millsToTimestamp = createFunction(MILLIS_TO_TIMESTAMP, millisToTimestampArgs);

                            mainFunction.setName(TIME_FLOOR);
                            ArrayList<Expression> timeFloorArgs = new ArrayList<>(Arrays.asList(millsToTimestamp, timeFloorWithFillFunArg1));
                            mainFunction.setParameters(new ExpressionList(timeFloorArgs));
                        }
                    }
                } else if (isTimeFloorWithFillFunction(mainFunction)) {
                    Expression timeFloorWithFillFunArg0 = mainFunction.getParameters().getExpressions().get(0);
                    Expression timeFloorWithFillFunArg1 = mainFunction.getParameters().getExpressions().get(1);
                    if (isUnixTimestampFunction(timeFloorWithFillFunArg0) && ((Function) timeFloorWithFillFunArg0).getParameters().getExpressions().get(0) instanceof Column) {
                        Function unixTimestampFun = (Function) timeFloorWithFillFunArg0;
                        mainFunction.setName(TIME_FLOOR);
                        List<Expression> timeFloorArgs = new ArrayList<>(Arrays.asList(unixTimestampFun.getParameters().getExpressions().get(0), timeFloorWithFillFunArg1));
                        mainFunction.setParameters(new ExpressionList(timeFloorArgs));
                    } else if (timeFloorWithFillFunArg0 instanceof Column) {
                        Multiplication multiplication = new Multiplication();
                        multiplication.setLeftExpression(timeFloorWithFillFunArg0);
                        multiplication.setRightExpression(new LongValue(1000));

                        List<Expression> millisToTimestampArgs = new ArrayList<>(Collections.singletonList(multiplication));
                        Function millsToTimestamp = createFunction(MILLIS_TO_TIMESTAMP, millisToTimestampArgs);

                        mainFunction.setName(TIME_FLOOR);
                        List<Expression> timeFloorArgs = new ArrayList<>(Arrays.asList(millsToTimestamp, timeFloorWithFillFunArg1));
                        mainFunction.setParameters(new ExpressionList(timeFloorArgs));
                    }
                }
            }
        }
    }

    private static class OrderByAdapter implements OrderByVisitor {
        @Override
        public void visit(OrderByElement orderBy) {
            if (orderBy.getExpression() == null) {
                return;
            }
            if (!(orderBy.getExpression() instanceof Function)) {
                return;
            }
            Function mainFunction = (Function) orderBy.getExpression();
            if (isFromUnixtimeFunction(mainFunction)) {
                Expression fromUnixtimeArg0 = mainFunction.getParameters().getExpressions().get(0);
                if (isTimeFloorWithFillFunction(fromUnixtimeArg0)) {
                    Function timeFloorWithFillFun = (Function) fromUnixtimeArg0;
                    Expression timeFloorWithFillFunArg0 = timeFloorWithFillFun.getParameters().getExpressions().get(0);
                    Expression timeFloorWithFillFunArg1 = timeFloorWithFillFun.getParameters().getExpressions().get(1);
                    if (isUnixTimestampFunction(timeFloorWithFillFunArg0) && ((Function) timeFloorWithFillFunArg0).getParameters().getExpressions().get(0) instanceof Column) {
                        Function unixTimestampFun = (Function) timeFloorWithFillFunArg0;
                        mainFunction.setName(TIME_FLOOR);
                        List<Expression> timeFloorArgs = new ArrayList<>(Arrays.asList(unixTimestampFun.getParameters().getExpressions().get(0), timeFloorWithFillFunArg1));
                        mainFunction.setParameters(new ExpressionList(timeFloorArgs));
                    } else if (timeFloorWithFillFunArg0 instanceof Column) {
                        Multiplication multiplication = new Multiplication();
                        multiplication.setLeftExpression(timeFloorWithFillFunArg0);
                        multiplication.setRightExpression(new LongValue(1000));

                        List<Expression> millisToTimestampArgs = new ArrayList<>(Collections.singletonList(multiplication));
                        Function millsToTimestamp = createFunction(MILLIS_TO_TIMESTAMP, millisToTimestampArgs);

                        mainFunction.setName(TIME_FLOOR);
                        ArrayList<Expression> timeFloorArgs = new ArrayList<>(Arrays.asList(millsToTimestamp, timeFloorWithFillFunArg1));
                        mainFunction.setParameters(new ExpressionList(timeFloorArgs));
                    }
                }
            } else if (isTimeFloorWithFillFunction(mainFunction)) {
                Expression timeFloorWithFillFunArg0 = mainFunction.getParameters().getExpressions().get(0);
                Expression timeFloorWithFillFunArg1 = mainFunction.getParameters().getExpressions().get(1);
                if (isUnixTimestampFunction(timeFloorWithFillFunArg0) && ((Function) timeFloorWithFillFunArg0).getParameters().getExpressions().get(0) instanceof Column) {
                    Function unixTimestampFun = (Function) timeFloorWithFillFunArg0;
                    mainFunction.setName(TIME_FLOOR);
                    List<Expression> timeFloorArgs = new ArrayList<>(Arrays.asList(unixTimestampFun.getParameters().getExpressions().get(0), timeFloorWithFillFunArg1));
                    mainFunction.setParameters(new ExpressionList(timeFloorArgs));
                } else if (timeFloorWithFillFunArg0 instanceof Column) {
                    Multiplication multiplication = new Multiplication();
                    multiplication.setLeftExpression(timeFloorWithFillFunArg0);
                    multiplication.setRightExpression(new LongValue(1000));

                    List<Expression> millisToTimestampArgs = new ArrayList<>(Collections.singletonList(multiplication));
                    Function millsToTimestamp = createFunction(MILLIS_TO_TIMESTAMP, millisToTimestampArgs);

                    mainFunction.setName(TIME_FLOOR);
                    List<Expression> timeFloorArgs = new ArrayList<>(Arrays.asList(millsToTimestamp, timeFloorWithFillFunArg1));
                    mainFunction.setParameters(new ExpressionList(timeFloorArgs));
                }
            }
        }
    }
}
