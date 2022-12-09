package com.mesalab.qgw.utils.sqlparser;

import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Maps;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.statement.values.ValuesStatement;
import org.apache.commons.compress.utils.Lists;

import java.util.List;
import java.util.Map;


/**
 *
 * @Classname InnerSelectItemVisitor
 * @Date 2021/7/20 3:39 下午
 * @Author wWei
 */
public class TopInnerSelectItemVisitor implements OrderByVisitor, GroupByVisitor, SelectItemVisitor, SelectVisitor {

    private List<Expression> groupByList;
    private List<Expression> OrderByList;
    private Map<String, String> exprAlias;

    public TopInnerSelectItemVisitor() {
        this.groupByList = Lists.newArrayList();
        this.OrderByList = Lists.newArrayList();
        this.exprAlias = Maps.newHashMap();
    }

    public void rewrite(PlainSelect plainSelect) {

        for (SelectItem selectItem : plainSelect.getSelectItems()) {
            selectItem.accept(this);
        }
        if (!StrUtil.isBlankIfStr(plainSelect.getGroupBy())) {
            plainSelect.getGroupBy().accept(this);
        }
        if (!StrUtil.isBlankIfStr(plainSelect.getOrderByElements())) {
            for (OrderByElement orderByElement : plainSelect.getOrderByElements()) {
                orderByElement.accept(this);
            }
        }
        plainSelect.accept(this);
    }

    @Override
    public void visit(GroupByElement groupByElement) {
        List<Expression> groupByExpressions = groupByElement.getGroupByExpressions();
        for (int i = 0; i < groupByExpressions.size(); i++) {
            Expression expression = groupByExpressions.get(i);
            if (exprAlias.containsKey(expression.toString())) {
                Column column = new Column(exprAlias.get(expression.toString()));
                groupByElement.getGroupByExpressions().set(i, column);
            }
        }
        groupByList.addAll(groupByExpressions);
    }

    @Override
    public void visit(OrderByElement orderByElement) {
        Expression expression = orderByElement.getExpression();
        String alias = exprAlias.get(expression.toString());
        if (exprAlias.containsKey(expression.toString())) {
            Column column = new Column(alias);
            orderByElement.setExpression(column);
            OrderByList.add(column);
        } else {
            OrderByList.add(expression);
        }
    }

    @Override
    public void visit(PlainSelect plainSelect) {
        List<SelectItem> selectItems = plainSelect.getSelectItems();
        List<Expression> list = Lists.newArrayList();
        list.addAll(OrderByList);
        list.addAll(groupByList);
        for (Expression expression : list) {
            if (exprAlias.containsKey(expression.toString())
                    || exprAlias.containsValue(expression.toString())) {
                continue;
            }
            SelectExpressionItem selectItem = new SelectExpressionItem();
            selectItem.setExpression(expression);
            selectItem.setAlias(new Alias(expression.toString()
                    .substring(0, expression.toString().indexOf("(")) + (int) (Math.random() * 100), true));
            selectItems.add(selectItem);
            exprAlias.put(selectItem.getExpression().toString(), selectItem.getAlias().getName());
        }
    }

    @Override
    public void visit(SetOperationList setOperationList) {

    }

    @Override
    public void visit(WithItem withItem) {

    }

    @Override
    public void visit(ValuesStatement valuesStatement) {

    }

    @Override
    public void visit(AllColumns allColumns) {
    }

    @Override
    public void visit(AllTableColumns allTableColumns) {
    }

    @Override
    public void visit(SelectExpressionItem selectExpressionItem) {
        if (!StrUtil.isBlankIfStr(selectExpressionItem.getAlias())) {
            exprAlias.put(selectExpressionItem.getExpression().toString(), selectExpressionItem.getAlias().getName());
        }
    }
}
