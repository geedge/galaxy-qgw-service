package com.mesalab.common.utils.sqlparser;

import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.geedgenetworks.utils.StringUtil;
import com.mesalab.common.exception.BusinessException;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.*;
import org.apache.commons.compress.utils.Lists;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


/**
 * @Classname TopSQLVisitor
 * @Date 2021/7/20 3:39 下午
 * @Author wWei
 */
public class TopSQLVisitor<T> implements OrderByVisitor<T>, GroupByVisitor<T>, SelectItemVisitor<T> {
    private static final Log log = LogFactory.get();
    private static final long TOP_INNER_LIMIT_RATIO = 10;
    private static final long TOP_INNER_LIMIT_LOWER = 1000;
    private static final Pattern pLimitSetting = Pattern.compile("\\blimit(.*?)(\\))", Pattern.CASE_INSENSITIVE);
    private List<String> groupByList;
    private List<String> orderByList;
    private List<String> selectItemExprList;
    private List<String> selectItemAliasList;
    private List<Function> functionList;
    private Select innerSelectBody = null;
    private Select originalSelectBody = null;

    public TopSQLVisitor() {
        this.selectItemExprList = Lists.newArrayList();
        this.selectItemAliasList = Lists.newArrayList();
        this.groupByList = Lists.newArrayList();
        this.orderByList = Lists.newArrayList();
        this.functionList = Lists.newArrayList();
    }

    /**
     * 验证SQL是否符合Top SQL优化条件：
     * Select expr,count/sum... from Table group by expr order by expr desc limit N
     *
     * @param sql
     * @return
     */
    public boolean isValid(String sql) {
        boolean isValid = false;
        try {
            Select select = ((Select) CCJSqlParserUtil.parse(sql));

            if (select instanceof PlainSelect) {
                this.originalSelectBody = select.getPlainSelect();
                innerSelectBody = getInnerSelectBody(select.getPlainSelect());
                if (StringUtil.isNotEmpty(innerSelectBody)) {
                    isValid = parseAndValidation((PlainSelect) innerSelectBody);
                }
            }
        } catch (JSQLParserException e) {
            isValid = false;
            log.error("Parse SQL error ", e);
        }

        return isValid;

    }

    public String rewrite() {
        try {
            String sql = innerSelectBody.toString();
            Select innerSelect = (Select) CCJSqlParserUtil.parse(sql);
            PlainSelect plainInnerSelect = innerSelect.getPlainSelect();
            new TopInnerSelectItemVisitor().rewrite(plainInnerSelect);

            Select outerSelect = (Select) CCJSqlParserUtil.parse(sql);
            PlainSelect plainOuterSelect = outerSelect.getPlainSelect();
            new TopOuterSelectItemVisitor().rewrite(plainOuterSelect, plainInnerSelect);
            return this.originalSelectBody.toString().replace(this.innerSelectBody.toString(), appendSetting(plainOuterSelect));
        } catch (JSQLParserException e) {
            throw new BusinessException("rewrite SQL error ", e);
        }
    }

    private String appendSetting(PlainSelect plainSelectOut) {
        rewriteInnerLimit(plainSelectOut);
        Matcher matcher = pLimitSetting.matcher(plainSelectOut.toString());
        StringBuffer sb = new StringBuffer();
        if (matcher.find()) {
            String replace = matcher.group(0).replace(")", " settings distributed_group_by_no_merge=1 )");
            matcher.appendReplacement(sb, replace);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private void rewriteInnerLimit(PlainSelect plainSelectOut) {
        FromItem fromItem = plainSelectOut.getFromItem();
        if (fromItem instanceof ParenthesedSelect) {
            Select select = ((ParenthesedSelect) fromItem).getSelect();
            if (select instanceof PlainSelect) {
                Limit limit = select.getPlainSelect().getLimit();
                if (StringUtil.isNotEmpty(limit)) {
                    long rowCount = Long.parseLong(limit.getRowCount().toString());
                    if (StringUtil.isNotEmpty(limit.getOffset())) {
                        long offset = Long.parseLong(limit.getOffset().toString());
                        LongValue longValue = new LongValue((offset + rowCount) * TOP_INNER_LIMIT_RATIO);
                        limit.setRowCount(longValue.getValue() < TOP_INNER_LIMIT_LOWER ? new LongValue(TOP_INNER_LIMIT_LOWER) : longValue);
                        limit.setOffset(null);
                    } else {
                        LongValue longValue = new LongValue(rowCount * TOP_INNER_LIMIT_RATIO);
                        limit.setRowCount(longValue.getValue() < TOP_INNER_LIMIT_LOWER ? new LongValue(TOP_INNER_LIMIT_LOWER) : longValue);
                    }
                }
            }
        }
    }

    /**
     * 递归遍历获取最内层SQL，内层SQL非标准查询将返回NULL
     *
     * @param plainSelect
     * @return
     */
    private PlainSelect getInnerSelectBody(PlainSelect plainSelect) {
        FromItem fromItem = plainSelect.getFromItem();
        if (fromItem instanceof ParenthesedSelect) {
            Select innerSelect = ((ParenthesedSelect) fromItem).getSelect();
            if (innerSelect instanceof PlainSelect) {
                return getInnerSelectBody(innerSelect.getPlainSelect());
            }
            return null;
        } else {
            return plainSelect;
        }
    }

    /**
     * 对当前SQL解析并验证是否符合TOP优化条件
     *
     * @param plainSelect
     * @return
     */
    private boolean parseAndValidation(PlainSelect plainSelect) {
        if (plainSelect == null) {
            return false;
        }
        for (SelectItem<?> item : plainSelect.getSelectItems()) {
            item.accept(this, null);
        }
        if (!StrUtil.isBlankIfStr(plainSelect.getGroupBy())) {
            plainSelect.getGroupBy().accept(this, null);
        }
        if (!StrUtil.isBlankIfStr(plainSelect.getOrderByElements())) {
            for (OrderByElement orderByElement : plainSelect.getOrderByElements()) {
                orderByElement.accept(this, null);
            }
        }

        if (selectItemAliasList.size() != selectItemExprList.size()) {
            return false;
        }

        if (groupByList.isEmpty() || orderByList.isEmpty()) {
            return false;
        }

        if (selectItemExprList.size() > (groupByList.size() + functionList.size())) {
            return false;
        }

        List unsupportedFunctionList = functionList.stream().filter(o ->
                StringUtil.isBlank(SQLFunctionUtil.getDistributedGroupByFunction(o.getName().toLowerCase(Locale.ROOT)))).collect(Collectors.toList());
        if (unsupportedFunctionList.size() > 0) {
            return false;
        }
        if (functionList.stream().anyMatch(o -> (o.isDistinct()) || o.isUnique())) {
            return false;
        }
        List unsupportedGroupByList = groupByList.stream().filter(o ->
                !selectItemExprList.contains(o) && !selectItemAliasList.contains(o)).collect(Collectors.toList());
        if (unsupportedGroupByList.size() > 0) {
            return false;
        }

        List unsupportedOrderByList = orderByList.stream().filter(o ->
                !selectItemExprList.contains(o) && !selectItemAliasList.contains(o)).collect(Collectors.toList());
        if (unsupportedOrderByList.size() > 0) {
            return false;
        }

        return true;
    }

    @Override
    public <S> T visit(GroupByElement groupByElement, S context) {
        ExpressionList<?> groupByExpressionList = groupByElement.getGroupByExpressionList();
        groupByExpressionList.forEach(o -> this.groupByList.add(o.toString()));
        return null;
    }

    @Override
    public <S> T visit(OrderByElement orderByElement, S context) {
        Expression expression = orderByElement.getExpression();
        this.orderByList.add(expression.toString());
        return null;
    }

    @Override
    public <S> T visit(SelectItem<? extends Expression> selectItem, S context) {
        Expression expression = selectItem.getExpression();
        this.selectItemExprList.add(expression.toString());
        if (expression instanceof Function) {
            functionList.add(((Function) expression));
        }
        if (StrUtil.isEmptyIfStr(selectItem.getAlias())) {
            if (expression instanceof Column) {
                Column column = (Column) (expression);
                Alias alias = new Alias(column.getColumnName());
                selectItem.setAlias(alias);
                this.selectItemAliasList.add(selectItem.getAlias().getName());
            } else if (expression instanceof Function) {
                selectItem.setAlias(new Alias("\"" + expression + "\""));
                this.selectItemAliasList.add(selectItem.getAlias().getName());
            }

        } else {
            this.selectItemAliasList.add(selectItem.getAlias().getName());
        }
        return null;
    }
}
