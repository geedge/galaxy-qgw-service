package com.mesalab.common.utils.sqlparser;

import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.OrderByVisitorAdapter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Parse conditional expression. This is the expression after a where clause.
 *
 * @Classname CondExpressionHelper
 * @Date 2023/2/28 17:31
 * @Author wWei
 */
public class CondExpressionHelper {

    /**
     * Desc: Gets the names of all the de-duplicated fields and parses the conditional expressions
     *
     * @param condExpr conditional expression after where
     * @return {@link List< String>}
     * @created by wWei
     * @date 2023/3/2 09:48
     */
    public static List<String> getDistinctFields(String condExpr) throws JSQLParserException {
        Expression expr = CCJSqlParserUtil.parseCondExpression(condExpr, false);
        ExprFieldParser parser = new ExprFieldParser();
        expr.accept(parser);
        return parser.getList().stream().distinct().collect(Collectors.toList());
    }

    /**
     * Desc: rewrite conditional expression, replace alias with exact value
     *
     * @param condExpr conditional expression after where
     * @param aliasFields      key-alias, value-ExactValue
     * @return {@link Expression}
     * @created by wWei
     * @date 2023/3/2 09:52
     */
    public static Expression rewireCondExprBaseOnCK(String condExpr, Map<String, String> aliasFields, boolean updateTable, String table) throws JSQLParserException {
        Expression expr = CCJSqlParserUtil.parseCondExpression(condExpr, false);
        ExprActualFieldParser exprParseAlias = new ExprActualFieldParser(aliasFields, updateTable, table);
        expr.accept(exprParseAlias);
        return expr;
    }

    public static List<String> getDistinctFieldsOfOrderBy(OrderByElement orderByElement) throws JSQLParserException {
        OrderByFieldParser orderBy = new OrderByFieldParser();
        orderByElement.accept(orderBy);
        return orderBy.getList();
    }

    /**
     * Desc: Get filed name
     *
     * @param column
     * @return {@link String}
     * @created by wWei
     * @date 2023/3/2 09:58
     */
    public static String getFieldName(Column column) {
        String columnName = column.getColumnName();
        if ((columnName.startsWith("\"") && columnName.endsWith("\""))
                || (columnName.startsWith("`") && columnName.endsWith("`"))) {
            columnName = columnName.substring(1, columnName.length() - 1);
        }
        return columnName;
    }

    private static class ExprFieldParser extends ExpressionVisitorAdapter {
        private final List<String> list = Lists.newArrayList();

        public List<String> getList() {
            return list;
        }

        @Override
        public void visit(Column column) {
            list.add(getFieldName(column));
        }
    }

    private static class ExprActualFieldParser extends ExpressionVisitorAdapter {
        private final Map<String, String> map;
        private final Boolean updateTable;
        private final String tableName;

        private ExprActualFieldParser(Map<String, String> map, Boolean updateTable, String tableName) {
            this.map = map;
            this.updateTable = updateTable;
            this.tableName = tableName;
        }

        @Override
        public void visit(Column column) {
            if (column.getTable() != null) {
                if (updateTable) {
                    column.getTable().setName(tableName);
                }
                return;
            }
            String columnName = getFieldName(column);
            if (map.containsKey(columnName)) {
                column.setColumnName(String.valueOf(map.get(columnName)));
            }
        }
    }

    private static class OrderByFieldParser extends OrderByVisitorAdapter {
        private final List<String> list = Lists.newArrayList();

        public List<String> getList() {
            return list;
        }

        @SneakyThrows
        @Override
        public void visit(OrderByElement orderBy) {
            list.addAll(getDistinctFields(String.valueOf(orderBy.getExpression())));
        }
    }

}

