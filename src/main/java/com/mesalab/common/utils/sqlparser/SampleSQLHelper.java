package com.mesalab.common.utils.sqlparser;

import lombok.SneakyThrows;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.*;

import java.util.List;

/**
 * TODO
 *
 * @Classname SampleSQLHelperOfWhereExpr
 * @Date 2023/3/22 10:42
 * @Author wWei
 */
public class SampleSQLHelper {

    private final String originalSQL;
    private final String filterExpr;
    private final Integer maxSize;
    private final Boolean isInvalidCondExpr;

    /**
     * Desc: TODO
     *
     * @param originalSQL       原始SQL
     * @param filterExpr        新增的 WHERE 过滤条件
     * @param isInvalidCondExpr 是否失效 HAVING 过滤条件
     * @param defaultSize       结果默认条数
     * @return {@link null}
     * @created by wWei
     * @date 2023/4/10 11:03
     */
    public SampleSQLHelper(String originalSQL, String filterExpr, Boolean isInvalidCondExpr, Integer defaultSize) {
        this.originalSQL = originalSQL;
        this.filterExpr = filterExpr;
        this.isInvalidCondExpr = isInvalidCondExpr;
        this.maxSize = defaultSize;
    }

    /**
     * Desc: 构建经过重置数据集、及失效WHERE和HAVING过滤条件后的SQL
     *
     * @param
     * @return {@link String}
     * @created by wWei
     * @date 2023/3/23 15:21
     */
    public String build() throws JSQLParserException {
        Select select = (Select) CCJSqlParserUtil.parse(originalSQL);
        select.accept(new SelectAdapter(), null);
        return select.toString();
    }

    private class FromItemAdapter extends FromItemVisitorAdapter<Object> {

        @Override
        public Object visit(ParenthesedSelect parenthesedSelect, Object context) {
            Select select = parenthesedSelect.getSelect();
            select.accept(new SelectAdapter(), null);
            return null;
        }
    }

    private class ExpressionAdapter extends ExpressionVisitorAdapter<Object> {

        @Override
        public Object visit(ParenthesedSelect parenthesedSelect, Object context) {
            Select select = parenthesedSelect.getSelect();
            select.accept(new SelectAdapter(), null);
            return null;
        }
    }

    private class SelectItemAdapter extends SelectItemVisitorAdapter<Object> {
        @Override
        public Object visit(SelectItem selectItem, Object context) {
            Expression expression = selectItem.getExpression();
            expression.accept(new ExpressionAdapter(), null);
            return null;
        }
    }


    private class SelectAdapter extends SelectVisitorAdapter<Object> {
        @SneakyThrows
        @Override
        public Object visit(PlainSelect plainSelect, Object context) {
            List<SelectItem<?>> selectItems = plainSelect.getSelectItems();
            if (selectItems != null) {
                for (SelectItem<?> selectItem : selectItems) {
                    selectItem.accept(new SelectItemAdapter(), null);
                }
            }

            FromItem fromItem = plainSelect.getFromItem();
            if (fromItem != null) {
                fromItem.accept(new FromItemAdapter(), null);
            }

            if ((fromItem instanceof Table) && filterExpr != null) {
                Expression expression = CCJSqlParserUtil.parseCondExpression(filterExpr, false);
                if (plainSelect.getWhere() != null) {
                    Expression where = plainSelect.getWhere();
                    if (isInvalidCondExpr) {
                        OrExpression orExpression = buildOrExpression(where);
                        plainSelect.setWhere(orExpression);
                        where = plainSelect.getWhere();
                    }
                    AndExpression andExpression = new AndExpression();

                    ParenthesedExpressionList<Expression> parenthesisLeft = new ParenthesedExpressionList<>();
                    parenthesisLeft.add(where);
                    ParenthesedExpressionList<Expression> parenthesisRight = new ParenthesedExpressionList<>();
                    parenthesisRight.add(expression);

                    andExpression.setLeftExpression(parenthesisLeft);
                    andExpression.setRightExpression(parenthesisRight);
                    plainSelect.setWhere(andExpression);
                } else {
                    plainSelect.setWhere(expression);
                }
            }

            Expression having = plainSelect.getHaving();
            if (having != null) {
                if (isInvalidCondExpr) {
                    OrExpression orExpression = buildOrExpression(having);
                    plainSelect.setHaving(orExpression);
                }
                having.accept(new ExpressionAdapter(), null);
            }

            Limit limit = plainSelect.getLimit();
            if (limit == null && maxSize != null) {
                Limit limitObj = new Limit();
                limitObj.setRowCount(new LongValue(maxSize));
                plainSelect.setLimit(limitObj);
            }
            return null;
        }

        @Override
        public Object visit(SetOperationList setOpList, Object context) {
            for (Select select : setOpList.getSelects()) {
                if (select instanceof PlainSelect) {
                    select.accept(this, null);
                }
            }
            return null;
        }

        private OrExpression buildOrExpression(Expression where) {
            OrExpression orExpression = new OrExpression();
            orExpression.setRightExpression(where);
            EqualsTo equalsTo = new EqualsTo();
            LongValue longValue = new LongValue(1);
            equalsTo.setLeftExpression(longValue);
            equalsTo.setRightExpression(longValue);
            orExpression.setLeftExpression(equalsTo);
            return orExpression;
        }
    }
}
