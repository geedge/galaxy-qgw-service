package com.mesalab.common.utils.sqlparser;

import cn.hutool.core.util.StrUtil;
import com.geedgenetworks.utils.StringUtil;
import com.google.common.collect.Maps;
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
 * @Classname OuterSelectItemVisitor
 * @Date
 * @Author
 */
public class TopOuterSelectItemVisitor implements SelectItemVisitor {
    public void rewrite(PlainSelect outerSelect, PlainSelect innerSelect) throws JSQLParserException {
        Select parse = (Select) CCJSqlParserUtil.parse("SELECT " + outerSelect.getSelectItems().stream().map(Object::toString)
                .collect(Collectors.joining(", ")) + " FROM ( " + innerSelect + ") ");
        PlainSelect plainSelect = parse.getPlainSelect();
        outerSelect.setFromItem(plainSelect.getFromItem());
        outerSelect.setWhere(null);
        outerSelect.setGroupByElement(innerSelect.getGroupBy());
        outerSelect.setHaving(innerSelect.getHaving());
        outerSelect.setOrderByElements(innerSelect.getOrderByElements());

        Map<String, String> exprAlias = Maps.newHashMap();
        for (SelectItem selectItem : innerSelect.getSelectItems()) {
            Expression expression = selectItem.getExpression();
            String alias = selectItem.getAlias().getName();
            exprAlias.put(expression.toString(), alias);
        }
        List<OrderByElement> orderByElements = innerSelect.getOrderByElements();
        for (OrderByElement orderByElement : orderByElements) {
            String orderByStr = orderByElement.getExpression().toString();
            if (exprAlias.containsKey(orderByStr)) {
                orderByElement.getExpression().accept(SQLVisitorUtil.getVisitorOfRenameColumn(exprAlias.get(orderByStr)), null);
            }
        }
        outerSelect.setOrderByElements(innerSelect.getOrderByElements());
        List<SelectItem<?>> selectItems = outerSelect.getSelectItems();
        for (SelectItem<?> selectItem : selectItems) {
            selectItem.accept(this, null);
        }
    }

    @Override
    public Object visit(SelectItem selectItem, Object context) {
        if (StrUtil.isBlankIfStr(selectItem.getAlias())) {
            return null;
        }
        String name = selectItem.getAlias().getName();
        Expression expression = selectItem.getExpression();
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
        return null;
    }
}
