package com.mesalab.common.utils.sqlparser;

import cn.hutool.core.util.StrUtil;
import com.mesalab.common.exception.BusinessException;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.*;
import org.apache.commons.compress.utils.Lists;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * TODO
 *
 * @Classname Validate
 * @Date 2021/7/20 3:39 下午
 * @Author wWei
 */
public class TopNSQLVisitor implements OrderByVisitor, GroupByVisitor, SelectItemVisitor {

    private static final Pattern pSelect = Pattern.compile("\\bselect\\s+", Pattern.CASE_INSENSITIVE);
    private static final Pattern pCount = Pattern.compile("\\bcount\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern pLimitSetting = Pattern.compile("\\blimit(.*?)(\\))", Pattern.CASE_INSENSITIVE);

    private List<String> groupByList;
    private List<String> orderByList;
    private boolean valid;
    private List<String> selectItemExprList;
    private List<String> selectItemAliasList;
    private int count;
    private SelectBody innerSelectBody = null;
    private SelectBody originalSelectBody = null;

    public TopNSQLVisitor() {
        this.selectItemExprList = Lists.newArrayList();
        this.selectItemAliasList = Lists.newArrayList();
        this.groupByList = Lists.newArrayList();
        this.orderByList = Lists.newArrayList();
        this.valid = true;
        this.count = 0;
    }

    public boolean valid(String sql) {
        Matcher matcher = pSelect.matcher(sql);
        while (matcher.find()) {
            this.count++;
        }
        SelectBody selectBody;
        try {
            selectBody = ((Select) CCJSqlParserUtil.parse(sql)).getSelectBody();
            this.originalSelectBody = selectBody;
        } catch (JSQLParserException e) {
            throw new BusinessException("parse SQL error ", e);
        }
        iterateSelect(selectBody);
        if (this.count != 0) {
            return this.valid = false;
        }
        setInnerSelectBody(selectBody);
        if (pCount.matcher(this.innerSelectBody.toString()).find()) {
            return this.valid = false;
        }
        parse((PlainSelect) this.innerSelectBody);
        validation();
        return this.valid;
    }

    public String rewrite() {
        String sql = this.innerSelectBody.toString();
        Select selectInner;
        try {
            selectInner = (Select) CCJSqlParserUtil.parse(sql);
            PlainSelect plainSelectInner = (PlainSelect) selectInner.getSelectBody();
            InnerSQLVisitor innerSQLVisitor = new InnerSQLVisitor();
            innerSQLVisitor.rewrite2Inner(plainSelectInner);

            Select selectOut = (Select) CCJSqlParserUtil.parse(sql);
            PlainSelect plainSelectOut = (PlainSelect) selectOut.getSelectBody();
            OutSQLVisitor outSQLVisitor = new OutSQLVisitor();
            outSQLVisitor.rewrite2Out(plainSelectOut, plainSelectInner);

            return this.originalSelectBody.toString().replace(this.innerSelectBody.toString(), appendSetting(plainSelectOut));
        } catch (JSQLParserException e) {
            throw new BusinessException("rewrite SQL error ", e);
        }
    }

    private String appendSetting(PlainSelect plainSelectOut) {
        Matcher matcher = pLimitSetting.matcher(plainSelectOut.toString());
        StringBuffer sb = new StringBuffer();
        if (matcher.find()) {
            String replace = matcher.group(0).replace(")", " settings distributed_group_by_no_merge=1 )");
            matcher.appendReplacement(sb, replace);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private void setInnerSelectBody(SelectBody selectBody) {
        if (selectBody instanceof PlainSelect) {
            FromItem fromItem = ((PlainSelect) selectBody).getFromItem();
            if (fromItem instanceof SubSelect) {
                setInnerSelectBody(((SubSelect) fromItem).getSelectBody());
            } else {
                this.innerSelectBody = selectBody;
            }
        }
    }

    private void iterateSelect(SelectBody selectBody) {
        if (selectBody instanceof PlainSelect) {
            this.count--;
            PlainSelect plainSelect = (PlainSelect) selectBody;
            FromItem fromItem = plainSelect.getFromItem();
            if (fromItem instanceof SubSelect) {
                SubSelect subSelect = (SubSelect) fromItem;
                iterateSelect(subSelect.getSelectBody());
            }
        }
    }

    private void validation() {
        if (selectItemAliasList.size() != selectItemExprList.size()) {
            this.valid = false;
        }

        if (this.groupByList.isEmpty() || this.orderByList.isEmpty()) {
            this.valid = false;
        }
        this.groupByList.forEach(o -> {
            if (!this.selectItemExprList.contains(o) && !this.selectItemAliasList.contains(o)) {
                this.valid = false;
            }
        });

        this.orderByList.forEach(o -> {
            if (!this.selectItemExprList.contains(o) && !this.selectItemAliasList.contains(o)) {
                this.valid = false;
            }
        });
    }

    private void parse(PlainSelect plainSelect) {
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
    }

    @Override
    public void visit(GroupByElement groupByElement) {
        groupByElement.getGroupByExpressions().forEach(o -> this.groupByList.add(o.toString()));
    }

    @Override
    public void visit(OrderByElement orderByElement) {
        Expression expression = orderByElement.getExpression();
        this.orderByList.add(expression.toString());
    }

    @Override
    public void visit(AllColumns allColumns) {
    }

    @Override
    public void visit(AllTableColumns allTableColumns) {
    }

    @Override
    public void visit(SelectExpressionItem selectExpressionItem) {
        this.selectItemExprList.add(selectExpressionItem.getExpression().toString());
        if (StrUtil.isEmptyIfStr(selectExpressionItem.getAlias())) {
            if (selectExpressionItem.getExpression() instanceof Column) {
                Column column = (Column) (selectExpressionItem.getExpression());
                Alias alias = new Alias(column.getColumnName());
                selectExpressionItem.setAlias(alias);
                this.selectItemAliasList.add(selectExpressionItem.getAlias().getName());
            }
        } else {
            this.selectItemAliasList.add(selectExpressionItem.getAlias().getName());
        }
    }
}
