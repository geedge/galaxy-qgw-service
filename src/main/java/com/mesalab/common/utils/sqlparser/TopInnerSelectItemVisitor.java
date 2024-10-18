package com.mesalab.common.utils.sqlparser;

import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Maps;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.*;
import org.apache.commons.compress.utils.Lists;

import java.util.List;
import java.util.Map;


/**
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

        for (SelectItem<?> selectItem : plainSelect.getSelectItems()) {
            selectItem.accept(this, null);
        }
        if (!StrUtil.isBlankIfStr(plainSelect.getGroupBy())) {
            plainSelect.getGroupBy().accept(this, null);
        }
        if (!StrUtil.isBlankIfStr(plainSelect.getOrderByElements())) {
            for (OrderByElement orderByElement : plainSelect.getOrderByElements()) {
                orderByElement.accept(this, null);
            }
        }
        plainSelect.accept(this, null);
    }

    @Override
    public Object visit(GroupByElement groupByElement, Object context) {
        ExpressionList<Expression> groupByExpressions = groupByElement.getGroupByExpressionList();
        for (int i = 0; i < groupByExpressions.size(); i++) {
            Expression expression = groupByExpressions.get(i);
            if (exprAlias.containsKey(expression.toString())) {
                Column column = new Column(exprAlias.get(expression.toString()));
                groupByElement.getGroupByExpressionList().set(i, column);
            }
        }
        groupByList.addAll(groupByExpressions);
        return null;
    }

    @Override
    public Object visit(OrderByElement orderByElement, Object context) {
        Expression expression = orderByElement.getExpression();
        String alias = exprAlias.get(expression.toString());
        if (exprAlias.containsKey(expression.toString())) {
            Column column = new Column(alias);
            orderByElement.setExpression(column);
            OrderByList.add(column);
        } else {
            OrderByList.add(expression);
        }
        return null;
    }

    @Override
    public Object visit(ParenthesedSelect parenthesedSelect, Object context) {
        return null;
    }

    @Override
    public Object visit(PlainSelect plainSelect, Object context) {
        List<SelectItem<?>> selectItems = plainSelect.getSelectItems();
        List<Expression> list = Lists.newArrayList();
        list.addAll(OrderByList);
        list.addAll(groupByList);
        for (Expression expression : list) {
            if (exprAlias.containsKey(expression.toString())
                    || exprAlias.containsValue(expression.toString())) {
                continue;
            }
            SelectItem<Expression> selectItem = new SelectItem<>();
            selectItem.setExpression(expression);
            selectItem.setAlias(new Alias(expression.toString()
                    .substring(0, expression.toString().indexOf("(")) + (int) (Math.random() * 100), true));
            selectItems.add(selectItem);
            exprAlias.put(selectItem.getExpression().toString(), selectItem.getAlias().getName());
        }
        return null;
    }

    @Override
    public Object visit(TableStatement tableStatement, Object context) {
        return null;
    }

    @Override
    public Object visit(LateralSubSelect lateralSubSelect, Object context) {
        return null;
    }

    @Override
    public Object visit(Values values, Object context) {
        return null;
    }

    @Override
    public Object visit(SelectItem selectItem, Object context) {
        if (!StrUtil.isBlankIfStr(selectItem.getAlias())) {
            exprAlias.put(selectItem.getExpression().toString(), selectItem.getAlias().getName());
        }
        return null;
    }

    @Override
    public Object visit(SetOperationList setOpList, Object context) {
        return null;
    }

    @Override
    public Object visit(WithItem withItem, Object context) {
        return null;
    }
}
