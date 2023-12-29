package com.mesalab.common.utils.sqlparser;

import cn.hutool.core.util.StrUtil;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Alias;
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
 * @Classname ClickhouseSampleSQLHelper
 * @Date 2023/3/22 10:42
 * @Author wWei
 */
public class SampleSQLHelper {

    private final String templateSQL;
    private final String originalSQL;
    private final boolean isInvalidCondExpr;

    /**
     * Desc: TODO
     *
     * @param originalSQL       原始SQL
     * @param dataSetTemplate   数据集SQL模版。表名通过解析 originalSQL 获取，不需指定，如：
     *                          1. SELECT * FROM {} LIMIT 0
     *                          2. SELECT * FROM {} WHERE rand() % 100 = 0 LIMIT 1000
     * @param isInvalidCondExpr 是否失效过滤条件
     * @return {@link null}
     * @created by wWei
     * @date 2023/3/23 15:17
     */
    public SampleSQLHelper(String originalSQL, String dataSetTemplate, boolean isInvalidCondExpr) {
        this.templateSQL = dataSetTemplate;
        this.originalSQL = originalSQL;
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
        public void visit(Table table) {
            if (table.getAlias() == null) {
                table.setAlias(new Alias(table.getName(), true));
            }
            table.setName(StrUtil.format("(" + templateSQL + ")", table.getFullyQualifiedName()));
            table.setSchemaName(null);
        }

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

            Expression where = plainSelect.getWhere();
            if (where != null) {
                if (isInvalidCondExpr) {
                    OrExpression orExpression = buildOrExpression(where);
                    plainSelect.setWhere(orExpression);
                }

                where.accept(new ExpressionAdapter());
            }

            Expression having = plainSelect.getHaving();
            if (having != null) {
                if (isInvalidCondExpr) {
                    OrExpression orExpression = buildOrExpression(having);
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
