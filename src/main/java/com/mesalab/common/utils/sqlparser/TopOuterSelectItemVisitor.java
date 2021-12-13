package com.mesalab.common.utils.sqlparser;

import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Maps;
import com.zdjizhi.utils.StringUtil;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 *
 * @Classname OuterSelectItemVisitor
 * @Date
 * @Author
 */
public class TopOuterSelectItemVisitor implements SelectItemVisitor {
    public void rewrite(PlainSelect outerSelect, PlainSelect innerSelect) throws JSQLParserException {
        Select parse = (Select) CCJSqlParserUtil.parse("SELECT " + outerSelect.getSelectItems().stream().map(Object::toString)
                .collect(Collectors.joining(", ")) + " FROM ( " + innerSelect + ") ");
        PlainSelect plainSelect = (PlainSelect) parse.getSelectBody();
        outerSelect.setFromItem(plainSelect.getFromItem());
        outerSelect.setWhere(null);
        outerSelect.setGroupByElement(innerSelect.getGroupBy());
        outerSelect.setHaving(innerSelect.getHaving());
        outerSelect.setOrderByElements(innerSelect.getOrderByElements());

        Map<String, String> aliasExpr = Maps.newHashMap();
        for (SelectItem selectItem : innerSelect.getSelectItems()) {
            SelectExpressionItem item = (SelectExpressionItem) selectItem;
            Expression expression = item.getExpression();
            String alias = item.getAlias().getName();
            aliasExpr.put(expression.toString(), alias);
        }
        List<OrderByElement> orderByElements = innerSelect.getOrderByElements();
        for (OrderByElement orderByElement : orderByElements) {
            if (aliasExpr.containsKey(orderByElement.getExpression().toString())) {
                ExpressionRewrite funColumnVisitor = new ExpressionRewrite();
                funColumnVisitor.replaceColumn(orderByElement.getExpression(), aliasExpr.get(orderByElement.getExpression().toString()));
            }
        }
        outerSelect.setOrderByElements(innerSelect.getOrderByElements());
        List<SelectItem> selectItems = outerSelect.getSelectItems();
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
                function.setAllColumns(false);
                String functionName = function.getName().toLowerCase(Locale.ROOT);
                if (StringUtil.isNotBlank(SQLFunctionUtil.getDistributedGroupByFunction(functionName))) {
                    function.setName(SQLFunctionUtil.getDistributedGroupByFunction(functionName));
                }
                Column column = new Column(name);
                ExpressionList expressionList = new ExpressionList(column);
                function.setParameters(expressionList);
            }
        }
    }
}
