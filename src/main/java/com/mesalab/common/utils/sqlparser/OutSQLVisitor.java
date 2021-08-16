package com.mesalab.common.utils.sqlparser;

import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Maps;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * TODO
 *
 * @Classname MyFromItemVisitor
 * @Date 2021/7/28 4:28 下午
 * @Author wWei
 */
public class OutSQLVisitor implements SelectItemVisitor {

    public void rewrite2Out(PlainSelect original, PlainSelect latest) throws JSQLParserException {
        Select parse = (Select) CCJSqlParserUtil.parse("SELECT " + original.getSelectItems().stream().map(Object::toString)
                .collect(Collectors.joining(", ")) + " FROM ( " + latest + ") ");
        PlainSelect plainSelect = (PlainSelect) parse.getSelectBody();
        original.setFromItem(plainSelect.getFromItem());
        original.setWhere(null);
        original.setGroupByElement(latest.getGroupBy());
        original.setHaving(latest.getHaving());
        original.setOrderByElements(latest.getOrderByElements());

        Map<String, String> aliasExpr = Maps.newHashMap();
        for (SelectItem selectItem : latest.getSelectItems()) {
            SelectExpressionItem selectExpressionItem = (SelectExpressionItem) selectItem;
            Expression expression = selectExpressionItem.getExpression();
            String name = selectExpressionItem.getAlias().getName();
            aliasExpr.put(expression.toString(), name);
        }
        List<OrderByElement> orderByElements = latest.getOrderByElements();
        for (OrderByElement orderByElement : orderByElements) {
            if (aliasExpr.containsKey(orderByElement.getExpression().toString())) {
                ExpressionRewrite funColumnVisitor = new ExpressionRewrite();
                funColumnVisitor.replaceColumn(orderByElement.getExpression(), aliasExpr.get(orderByElement.getExpression().toString()));
            }
        }
        original.setOrderByElements(latest.getOrderByElements());
        List<SelectItem> selectItems = original.getSelectItems();
        for (SelectItem selectItem : selectItems) {
            SelectExpressionItem selectExpressionItem = (SelectExpressionItem) selectItem;
            selectExpressionItem.accept(this);
        }
    }

    @Override
    public void visit(AllColumns allColumns) {
    }

    @Override
    public void visit(AllTableColumns allTableColumns) {
    }

    @Override
    public void visit(SelectExpressionItem selectExpressionItem) {
        if (StrUtil.isBlankIfStr(selectExpressionItem.getAlias())) {
            return;
        }
        String name = selectExpressionItem.getAlias().getName();
        Expression expression = selectExpressionItem.getExpression();
        if (!StrUtil.isBlankIfStr(name)) {
            if (expression instanceof Column) {
                Column column = (Column) expression;
                column.setColumnName(name);
            } else if (expression instanceof Function) {
                Function function = (Function) expression;
                Column column = new Column(name);
                ExpressionList expressionList = new ExpressionList(column);
                function.setParameters(expressionList);
            }
        }
    }
}
