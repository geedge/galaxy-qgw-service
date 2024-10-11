package com.mesalab.common.utils.sqlparser;

import lombok.SneakyThrows;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.statement.values.ValuesStatement;

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
        select.getSelectBody().accept(selectAdapter);
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


    private static class SelectAdapter extends SelectVisitorAdapter {
        private boolean isBasic = true;

        @SneakyThrows
        @Override
        public void visit(PlainSelect plainSelect) {
            List<SelectItem> selectItems = plainSelect.getSelectItems();
            if (selectItems != null) {
                for (SelectItem selectItem : selectItems) {
                    if (selectItem instanceof SelectExpressionItem) {
                        SelectExpressionItem selectExpressionItem = (SelectExpressionItem) selectItem;
                        Expression expression = selectExpressionItem.getExpression();
                        if (expression instanceof SubSelect) {
                            isBasic = false;
                            return;
                        }
                    }
                }
            }

            FromItem fromItem = plainSelect.getFromItem();
            if (!(fromItem instanceof Table)) {
                isBasic = false;
                return;
            }
            List<Join> joins = plainSelect.getJoins();
            if (joins != null && !joins.isEmpty()) {
                isBasic = false;
                return;
            }

            if (plainSelect.getWhere() != null) {
                Expression where = plainSelect.getWhere();
                ExpressionAdapter expressionAdapter = new ExpressionAdapter();
                where.accept(expressionAdapter);
                if (!expressionAdapter.isBasic) {
                    isBasic = false;
                    return;
                }
            }

            GroupByElement groupBy = plainSelect.getGroupBy();
            if (groupBy == null) {
                isBasic = false;
                return;
            }


            for (Expression expression : groupBy.getGroupByExpressionList().getExpressions()) {
                ExpressionAdapter expressionAdapter = new ExpressionAdapter();
                expression.accept(expressionAdapter);
                if (!expressionAdapter.isBasic) {
                    isBasic = false;
                    return;
                }
            }


            List<OrderByElement> orderByElements = plainSelect.getOrderByElements();
            for (OrderByElement orderByElement : orderByElements) {
                Expression expression = orderByElement.getExpression();
                ExpressionAdapter expressionAdapter = new ExpressionAdapter();
                expression.accept(expressionAdapter);
                if (!expressionAdapter.isBasic) {
                    isBasic = false;
                    return;
                }
            }

            if (plainSelect.getHaving() != null) {
                Expression having = plainSelect.getHaving();
                ExpressionAdapter expressionAdapter = new ExpressionAdapter();
                having.accept(expressionAdapter);
                if (!expressionAdapter.isBasic) {
                    isBasic = false;
                    return;
                }
            }
            if(plainSelect.getLimit() != null && plainSelect.getLimit().getOffset() != null){
                isBasic = false;
                return;

            }
        }

        @Override
        public void visit(SetOperationList setOpList) {
            isBasic = false;
            return;
        }

        @Override
        public void visit(WithItem withItem) {
            isBasic = false;
            return;
        }

        @Override
        public void visit(ValuesStatement aThis) {
            isBasic = false;
            return;
        }
    }

    private static class ExpressionAdapter extends ExpressionVisitorAdapter {
        private boolean isBasic = true;

        @Override
        public void visit(SubSelect subSelect) {
            isBasic = false;
            return;
        }

    }

}