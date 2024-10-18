package com.mesalab.common.utils.sqlparser;

import lombok.SneakyThrows;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;

import java.util.List;

/**
 * TODO
 *
 * @Classname TimeBasedShardingUtil
 * @Date 2024/9/14 14:51
 * @Author wWei
 */
public class TimeBasedShardingUtil {
    private TimeBasedShardingUtil() {
    }

    /**
     * Desc: TODO
     *
     * @param sql
     * @return {@link boolean}
     * @created by wWei
     * @date 2024/10/10 14:58
     */
    public static boolean isSlicingBaseSupported(String sql) {
        Statement parse;
        try {
            parse = CCJSqlParserUtil.parse(sql);
        } catch (JSQLParserException e) {
            return false;
        }
        if (!(parse instanceof Select)) {
            return false;
        }
        Select select = (Select) parse;
        SelectAdapter selectAdapter = new SelectAdapter();
        if (select instanceof PlainSelect) {
            select.getPlainSelect().accept(selectAdapter, null);
        } else if (select instanceof SetOperationList) {
            select.getSetOperationList().accept(selectAdapter, null);
        }
        if (!selectAdapter.isBasic) {
            return false;
        }
        return true;
    }

    public static void main(String[] args) {
        //String sql = "select ((select 1 from table2)) as a,field1, SUM(field2) as metric from (select * from table2) as www where 1 =1 AND a in (select 1 from table limit 1) group by (select 1 from table limit 1) order by (select 1 from table limit 1) desc limit 100";
        String sql = "select a as a,field1, SUM(field2) as metric from table as www where 1 =1 AND a in (1) group by field1 order by a asc,(select 1 from table limit 1) desc limit 100";
        boolean valid = isSlicingBaseSupported(sql);
        System.out.println(valid);
    }


    private static class SelectAdapter extends SelectVisitorAdapter<Object> {
        private boolean isBasic = true;

        @SneakyThrows
        @Override
        public Object visit(PlainSelect plainSelect, Object context) {
            List<SelectItem<?>> selectItems = plainSelect.getSelectItems();
            if (selectItems != null) {
                for (SelectItem<?> selectItem : selectItems) {
                    Expression expression = selectItem.getExpression();
                    if (expression instanceof ParenthesedSelect) {
                        isBasic = false;
                        return null;
                    }
                }
            }

            FromItem fromItem = plainSelect.getFromItem();
            if (!(fromItem instanceof Table)) {
                isBasic = false;
                return null;
            }
            List<Join> joins = plainSelect.getJoins();
            if (joins != null && !joins.isEmpty()) {
                isBasic = false;
                return null;
            }

            if (plainSelect.getWhere() != null) {
                Expression where = plainSelect.getWhere();
                ExpressionAdapter expressionAdapter = new ExpressionAdapter();
                where.accept(expressionAdapter, null);
                if (!expressionAdapter.isBasic) {
                    isBasic = false;
                    return null;
                }
            }

            GroupByElement groupBy = plainSelect.getGroupBy();
            if (groupBy == null) {
                isBasic = false;
                return null;
            }

            ExpressionList<Expression> groupByExpressionList = groupBy.getGroupByExpressionList();
            for (Expression expression : groupByExpressionList) {
                ExpressionAdapter expressionAdapter = new ExpressionAdapter();
                expression.accept(expressionAdapter, null);
                if (!expressionAdapter.isBasic) {
                    isBasic = false;
                    return null;
                }
            }


            List<OrderByElement> orderByElements = plainSelect.getOrderByElements();
            for (OrderByElement orderByElement : orderByElements) {
                Expression expression = orderByElement.getExpression();
                ExpressionAdapter expressionAdapter = new ExpressionAdapter();
                expression.accept(expressionAdapter, null);
                if (!expressionAdapter.isBasic) {
                    isBasic = false;
                    return null;
                }
            }

            if (plainSelect.getHaving() != null) {
                Expression having = plainSelect.getHaving();
                ExpressionAdapter expressionAdapter = new ExpressionAdapter();
                having.accept(expressionAdapter, null);
                if (!expressionAdapter.isBasic) {
                    isBasic = false;
                    return null;
                }
            }
            if (plainSelect.getLimit() != null && plainSelect.getLimit().getOffset() != null) {
                isBasic = false;
                return null;

            }
            return null;
        }

        @Override
        public Object visit(SetOperationList setOpList, Object context) {
            isBasic = false;
            return null;
        }

        @Override
        public Object visit(WithItem withItem, Object context) {
            isBasic = false;
            return null;
        }

        @Override
        public Object visit(Values aThis, Object context) {
            isBasic = false;
            return null;
        }
    }

    private static class ExpressionAdapter extends ExpressionVisitorAdapter<Object> {
        private boolean isBasic = true;

        @Override
        public Object visit(Select select, Object context) {
            isBasic = false;
            return null;
        }

    }

}