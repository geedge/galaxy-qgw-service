package com.mesalab.common.utils.sqlparser;

import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.collect.Maps;
import com.mesalab.common.exception.BusinessException;
import com.zdjizhi.utils.StringUtil;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.*;
import org.apache.commons.compress.utils.Lists;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


/**
 * TODO
 *
 * @Classname Validate
 * @Date 2021/7/20 3:39 下午
 * @Author wWei
 */
public class TopSQLVisitor implements OrderByVisitor, GroupByVisitor, SelectItemVisitor {
    private static final Log log = LogFactory.get();
    private static final Pattern pSelect = Pattern.compile("\\bselect\\s+", Pattern.CASE_INSENSITIVE);
    private static final Pattern pCount = Pattern.compile("\\bcount\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern pUniq = Pattern.compile("\\buniq\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern pUniqExact = Pattern.compile("\\buniqExact\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern pUniqCombined = Pattern.compile("\\buniqCombined\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern pUniqCombined64 = Pattern.compile("\\buniqCombined64\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern pUniqHLL12 = Pattern.compile("\\buniqHLL12\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern pUniqTheta = Pattern.compile("\\buniqTheta\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern pLimitSetting = Pattern.compile("\\blimit(.*?)(\\))", Pattern.CASE_INSENSITIVE);

    private List<String> groupByList;
    private List<String> orderByList;
    private List<String> selectItemExprList;
    private List<String> selectItemAliasList;
    private List<String> functionList;
    private int count;
    private SelectBody innerSelectBody = null;
    private SelectBody originalSelectBody = null;

    public TopSQLVisitor() {
        this.selectItemExprList = Lists.newArrayList();
        this.selectItemAliasList = Lists.newArrayList();
        this.groupByList = Lists.newArrayList();
        this.orderByList = Lists.newArrayList();
        this.functionList = Lists.newArrayList();
        this.count = 0;
    }

    /**
     * 验证SQL是否符合Top SQL优化条件：
     *  Select expr,count/sum... from Table group by expr order by expr desc limit N
     * @param sql
     * @return
     */
    public boolean isValid(String sql) {
        boolean isValid = false;
        try {
            Matcher matcher = pSelect.matcher(sql);
            while (matcher.find()) {
                this.count++;
            }
            SelectBody selectBody = ((Select) CCJSqlParserUtil.parse(sql)).getSelectBody();
            this.originalSelectBody = selectBody;
            iterateSelect(selectBody);
            if (this.count != 0) {
                isValid = false;
            } else {
                innerSelectBody = getInnerSelectBody(selectBody);
                if (StringUtil.isNotEmpty(innerSelectBody)) {
                    isValid = parseAndValidation((PlainSelect) innerSelectBody);
                } else {
                    isValid = false;
                }
            }

        } catch (JSQLParserException e) {
            isValid = false;
            log.error("Parse SQL error ", e);
        } finally {
            return isValid;
        }
    }



    private boolean findCount(String inner) {
        if (pCount.matcher(inner).find()) {
            return true;
        }
        if (pUniq.matcher(inner).find()) {
            return true;
        }
        if (pUniqExact.matcher(inner).find()) {
            return true;
        }
        if (pUniqCombined.matcher(inner).find()) {
            return true;
        }
        if (pUniqCombined64.matcher(inner).find()) {
            return true;
        }
        if (pUniqHLL12.matcher(inner).find()) {
            return true;
        }
        if (pUniqTheta.matcher(inner).find()) {
            return true;
        }
        return false;
    }

    public String rewrite() {
        String sql = innerSelectBody.toString();
        Select innerSelect;
        try {
            innerSelect = (Select) CCJSqlParserUtil.parse(sql);
            PlainSelect plainInnerSelect = (PlainSelect) innerSelect.getSelectBody();
            TopInnerSelectItemVisitor innerSelectItemVisitor = new TopInnerSelectItemVisitor();
            innerSelectItemVisitor.rewrite(plainInnerSelect);

            Select outerSelect = (Select) CCJSqlParserUtil.parse(sql);
            PlainSelect plainOuterSelect = (PlainSelect) outerSelect.getSelectBody();
            TopOuterSelectItemVisitor outerSelectItemVisitor = new TopOuterSelectItemVisitor();
            outerSelectItemVisitor.rewrite(plainOuterSelect, plainInnerSelect);
            return this.originalSelectBody.toString().replace(this.innerSelectBody.toString(), appendSetting(plainOuterSelect));
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

    private SelectBody getInnerSelectBody(SelectBody selectBody) {
        if (selectBody instanceof PlainSelect) {
            FromItem fromItem = ((PlainSelect) selectBody).getFromItem();
            if (fromItem instanceof SubSelect) {
                return getInnerSelectBody(((SubSelect) fromItem).getSelectBody());
            } else {
                return selectBody;
            }
        } else {
            return null;
        }
    }

    private void iterateSelect(SelectBody selectBody) {
        this.count--;
        if (selectBody instanceof PlainSelect) {
            PlainSelect plainSelect = (PlainSelect) selectBody;
            FromItem fromItem = plainSelect.getFromItem();
            if (fromItem instanceof SubSelect) {
                SubSelect subSelect = (SubSelect) fromItem;
                iterateSelect(subSelect.getSelectBody());
            }
        }
    }

    private boolean parseAndValidation(PlainSelect plainSelect) {

        for (SelectItem item : plainSelect.getSelectItems()) {
            item.accept(this);
        }
        if (!StrUtil.isBlankIfStr(plainSelect.getGroupBy())) {
            plainSelect.getGroupBy().accept(this);
        }
        if (!StrUtil.isBlankIfStr(plainSelect.getOrderByElements())) {
            for (OrderByElement orderByElement : plainSelect.getOrderByElements()) {
                orderByElement.accept(this);
            }
        }

        if (selectItemAliasList.size() != selectItemExprList.size()) {
            return false;
        }

        if (groupByList.isEmpty() || orderByList.isEmpty()) {
           return false;
        }

        List unsupportedFunctionList =  functionList.stream().filter(o -> StringUtil.isBlank(SQLFunctionUtil.getDistributedGroupByFunction(o))).collect(Collectors.toList());
        if(unsupportedFunctionList.size() > 0) {
            return false;
        }

       List unsupportedGroupByList =  groupByList.stream().filter(o -> !selectItemExprList.contains(o) && !selectItemAliasList.contains(o) ).collect(Collectors.toList());
        if(unsupportedGroupByList.size() > 0) {
            return false;
        }

        List unsupportedOrderByList =  orderByList.stream().filter(o -> !selectItemExprList.contains(o) && !selectItemAliasList.contains(o) ).collect(Collectors.toList());
        if(unsupportedOrderByList.size() > 0) {
            return false;
        }

        return true;
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
       Expression expression = selectExpressionItem.getExpression();
       this.selectItemExprList.add(expression.toString());
        if (expression instanceof Function) {
            functionList.add(((Function) expression).getName().toLowerCase(Locale.ROOT));
        }
       if (StrUtil.isEmptyIfStr(selectExpressionItem.getAlias())) {
           if (expression instanceof Column) {
               Column column = (Column) (expression);
               Alias alias = new Alias(column.getColumnName());
               selectExpressionItem.setAlias(alias);
               this.selectItemAliasList.add(selectExpressionItem.getAlias().getName());
           }

       } else {
           this.selectItemAliasList.add(selectExpressionItem.getAlias().getName());
       }
    }
}
