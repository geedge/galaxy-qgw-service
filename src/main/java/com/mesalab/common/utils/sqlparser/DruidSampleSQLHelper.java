package com.mesalab.common.utils.sqlparser;

import lombok.SneakyThrows;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.*;

import java.util.List;

/**
 * TODO
 *
 * @Classname DruidSampleSQLHelper
 * @Date 2023/3/22 10:42
 * @Author wWei
 */
public class DruidSampleSQLHelper {

    private final String originalSQL;
    private final String filterExpr;
    private final boolean isInvalidCondExpr;

    /**
     * Desc: TODO
     *
     * @param originalSQL       原始SQL
     * @param filterExpr        置换后的 WHERE 过滤条件
     * @param isInvalidCondExpr 是否失效 HAVING 过滤条件
     * @return {@link null}
     * @created by wWei
     * @date 2023/4/10 11:03
     */
    public DruidSampleSQLHelper(String originalSQL, String filterExpr, boolean isInvalidCondExpr) {
        this.originalSQL = originalSQL;
        this.filterExpr = filterExpr;
        this.isInvalidCondExpr = isInvalidCondExpr;
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
        select.getSelectBody().accept(new SelectAdapter());
        return select.toString();
    }

    private class FromItemAdapter extends FromItemVisitorAdapter {

        @Override
        public void visit(SubSelect subSelect) {
            SelectBody selectBody = subSelect.getSelectBody();
            selectBody.accept(new SelectAdapter());
        }
    }

    private class ExpressionAdapter extends ExpressionVisitorAdapter {

        @Override
        public void visit(SubSelect subSelect) {
            SelectBody selectBody = subSelect.getSelectBody();
            selectBody.accept(new SelectAdapter());
        }
    }

    private class SelectItemAdapter extends SelectItemVisitorAdapter {
        @Override
        public void visit(SelectExpressionItem item) {
            item.getExpression().accept(new ExpressionAdapter());
        }
    }

    private class SelectAdapter extends SelectVisitorAdapter {
        @SneakyThrows
        @Override
        public void visit(PlainSelect plainSelect) {
            List<SelectItem> selectItems = plainSelect.getSelectItems();
            if (selectItems != null) {
                for (SelectItem selectItem : selectItems) {
                    selectItem.accept(new SelectItemAdapter());
                }
            }

            FromItem fromItem = plainSelect.getFromItem();
            if (fromItem != null) {
                fromItem.accept(new FromItemAdapter());
            }

            if ((fromItem instanceof Table) && plainSelect.getWhere() != null) {
                plainSelect.setWhere(CCJSqlParserUtil.parseCondExpression(filterExpr));
            }

            Expression having = plainSelect.getHaving();
            if (having != null) {
                if (isInvalidCondExpr) {
                    OrExpression orExpression = new OrExpression();
                    orExpression.setRightExpression(having);
                    EqualsTo equalsTo = new EqualsTo();
                    LongValue longValue = new LongValue(1);
                    equalsTo.setLeftExpression(longValue);
                    equalsTo.setRightExpression(longValue);
                    orExpression.setLeftExpression(equalsTo);
                    plainSelect.setHaving(orExpression);
                }
                having.accept(new ExpressionAdapter());
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
}
