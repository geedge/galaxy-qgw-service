package com.mesalab.common.utils.sqlparser;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.geedgenetworks.utils.StringUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mesalab.common.enums.HttpStatusCodeEnum;
import com.mesalab.common.utils.SpringContextUtil;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.qgw.service.SQLSyncQueryService;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.Between;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.ItemsList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;

import java.util.*;

/**
 * sql解析工具
 *
 * @Classname SQLSyntaxParserUtil
 * @Date 2021/1/15 5:21 下午
 * @Author wWei
 */
public class SQLSyntaxParserUtil {

    private static final Log log = LogFactory.get();

    private static SQLSyncQueryService queryService = (SQLSyncQueryService) SpringContextUtil.getBean("queryService");

    public static List syntaxParse(String sql) {
        List resultList = Lists.newArrayList();
        try {
            parseSQL(sql, resultList);
            //addTypeForSelectItems(sql, resultList);
        } catch (RuntimeException | JSQLParserException e) {
            log.error("syntax_parse: sql-" + sql + ", error: ", e);
            throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.BAD_REQUEST_SQL_SYNTAX_PARSE_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.BAD_REQUEST_SQL_SYNTAX_PARSE_EXCEPTION.getMessage(),e.getMessage()));
        }
        return resultList;
    }

    private static void parseSQL(String sql, List result) throws RuntimeException, JSQLParserException {
        Statement parse = null;
        parse = CCJSqlParserUtil.parse(sql);
        SQLStructure sqlStructure = new SQLStructure();
        if (!(parse instanceof Select)) return;
        Select select = (Select) parse;
        SelectBody selectBody = select.getSelectBody();
        if (selectBody instanceof PlainSelect) {
            PlainSelect plainSelect = (PlainSelect) selectBody;
            sqlStructure.setSelect(getSelectItems(plainSelect.getSelectItems()));
            sqlStructure.setFrom(getFromItems(plainSelect.getFromItem()));
            sqlStructure.setWhere(getWhereItems(plainSelect.getWhere()));
            sqlStructure.setGroupBy(getGroupByItems(plainSelect.getGroupBy()));
            sqlStructure.setHaving(getWhereItems(plainSelect.getHaving()));
            sqlStructure.setOrderBy(getOrderBy(plainSelect.getOrderByElements()));
            sqlStructure.setLimit(getLimit(plainSelect.getLimit(), plainSelect.getOffset()));
            result.add(sqlStructure);
        } else if (selectBody instanceof SetOperationList) {
            SetOperationList setOperationList = (SetOperationList) selectBody;
            List<SelectBody> selects = setOperationList.getSelects();
            for (int i = 0; i < selects.size(); i++) {
                parseSQL(selects.get(i).toString(), result);
                if (i + 1 >= selects.size()) return;
                Map<String, Object> conMap = Maps.newHashMap();
                conMap.put(CONNECTOR, setOperationList.getOperations().get(i).toString());
                result.add(conMap);
            }
        } else {
            throw new RuntimeException("Parse SelectBody Error, Not Support This.");
        }
    }

    private static Map<String, Object> getLimit(Limit limit, Offset offset) {
        if (StringUtil.isEmpty(limit)) {
            return null;
        }
        Map<String, Object> map = Maps.newHashMap();
        map.put("rowCount", Long.parseLong(limit.getRowCount().toString()));
        if (StringUtil.isEmpty(offset)) {
            map.put(ITEM, limit.toString());
            map.put("offset", limit.getOffset() != null ? Long.parseLong(limit.getOffset().toString()) : null);
        } else {
            map.put(ITEM, limit.toString().concat(offset.toString()));
            map.put("offset", offset.getOffset());
        }
        return map;
    }

    private static List<Map<String, Object>> getOrderBy(List<OrderByElement> orderByElements) {
        if (StringUtil.isEmpty(orderByElements)) {
            return null;
        }
        List<Map<String, Object>> list = Lists.newArrayList();
        for (OrderByElement orderByElement : orderByElements) {
            Map<String, Object> map = Maps.newHashMap();
            map.put(ITEM, orderByElement.toString());
            map.put("element", orderByElement.getExpression().toString());
            map.put("type", orderByElement.isAsc() ? "ASC" : "DESC");
            list.add(map);
        }
        return list;
    }

    private static List<Map<String, Object>> getGroupByItems(GroupByElement groupBy) {
        if (StringUtil.isEmpty(groupBy)) {
            return null;
        }
        List<Expression> groupByExpressions = groupBy.getGroupByExpressions();
        List<Map<String, Object>> resultList = Lists.newArrayList();
        for (Expression groupByExpression : groupByExpressions) {
            Map<String, Object> map = Maps.newHashMap();
            map.put(ITEM, groupByExpression.toString());
            resultList.add(map);
        }
        return resultList;
    }

    private static Map<String, Object> getWhereItems(Expression where) {
        if (StringUtil.isEmpty(where)) {
            return null;
        }
        Map<String, Object> resultMap = Maps.newHashMap();
        resultMap.put(ITEM, where.toString());
        resultMap.put(EXPRESSION, getExpression(where));
        return resultMap;
    }

    private static List<Map<String, Object>> getFromItems(FromItem fromItem) {
        List<Map<String, Object>> resultList = Lists.newArrayList();
        Map<String, Object> itemMap = Maps.newHashMap();
        if (fromItem instanceof Table) {
            Table table = (Table) fromItem;
            itemMap.put(NODE, TABLE);
            itemMap.put(VALUE_L, table.toString());
        } else if (fromItem instanceof SubSelect) {
            SubSelect subSelect = (SubSelect) fromItem;
            itemMap.put(NODE, SUB_SELECT);
            itemMap.put(VALUE_L, subSelect.toString());
        } else {
            throw new RuntimeException("Parse FromItem Error, Not Support This.");
        }
        resultList.add(itemMap);
        return resultList;
    }

    private static List<Map<String, Object>> getSelectItems(List<SelectItem> selectItemList) {

        List<Map<String, Object>> resultItems = Lists.newArrayList();
        for (SelectItem item : selectItemList) {
            Map<String, Object> itemMap = Maps.newHashMap();
            Map<String, Object> exp = Maps.newHashMap();
            if (item instanceof AllTableColumns) {
                AllTableColumns allTableColumns = (AllTableColumns) item;
                itemMap.put(ITEM, allTableColumns.toString());
                exp.put(NODE, COLUMN);
                exp.put(NAME, allTableColumns.toString());
                itemMap.put(EXPRESSION, exp);
            } else if (item instanceof AllColumns) {
                AllColumns allColumns = (AllColumns) item;
                itemMap.put(ITEM, allColumns.toString());
                exp.put(NODE, COLUMN);
                exp.put(NAME, allColumns.toString());
                itemMap.put(EXPRESSION, exp);
            } else if (item instanceof SelectExpressionItem) {
                SelectExpressionItem selectExpressionItem = (SelectExpressionItem) item;
                Expression expression = selectExpressionItem.getExpression();
                itemMap.put(ITEM, item.toString());
                itemMap.put(EXPRESSION, getExpression(expression));
                Optional<Alias> alias = Optional.ofNullable(selectExpressionItem.getAlias());
                alias.ifPresent(o -> {
                    exp.put(NAME, o.getName());
                    exp.put(USEAS, o.isUseAs());
                    itemMap.put(ALIAS, exp);
                });
            } else {
                throw new RuntimeException("Parse SelectItemList Error, Not Support This.");
            }
            resultItems.add(itemMap);
        }
        return resultItems;
    }

    private static Map<String, Object> getExpression(Expression expression) {
        Map<String, Object> resultMap = Maps.newHashMap();
        if (expression instanceof Column) {
            Column column = (Column) expression;
            resultMap.put(NODE, COLUMN);
            resultMap.put(NAME, column.getColumnName());
        } else if (expression instanceof LongValue) {
            LongValue longValue = (LongValue) expression;
            resultMap.put(NODE, VALUE_U);
            resultMap.put(VALUE_L, longValue.getValue());
        } else if (expression instanceof StringValue) {
            StringValue stringValue = (StringValue) expression;
            resultMap.put(NODE, VALUE_U);
            resultMap.put(VALUE_L, stringValue.getValue());
        } else if (expression instanceof Function) {
            Function function = (Function) expression;
            resultMap.put(NODE, FUNCTION);
            resultMap.put(NAME, function.getName());
            Optional.ofNullable(function.getParameters()).ifPresent(o -> {
                Map<String, Object> parameters = getParameters(function.getParameters().getExpressions());
                resultMap.put(PARAMETERS, parameters);
            });
        } else if (expression instanceof AndExpression) {
            AndExpression andExpression = (AndExpression) expression;
            resultMap.put(NODE, AND_EXPRESSION);
            resultMap.put(LEFT_EXPRESSION, getExpression(andExpression.getLeftExpression()));
            resultMap.put(RIGHT_EXPRESSION, getExpression(andExpression.getRightExpression()));
        } else if (expression instanceof OrExpression) {
            OrExpression andExpression = (OrExpression) expression;
            resultMap.put(NODE, OR_EXPRESSION);
            resultMap.put(LEFT_EXPRESSION, getExpression(andExpression.getLeftExpression()));
            resultMap.put(RIGHT_EXPRESSION, getExpression(andExpression.getRightExpression()));
        } else if (expression instanceof Between) {
            Between between = (Between) expression;
            resultMap.put(NODE, BETWEEN);
            resultMap.put(LEFT_EXPRESSION, getExpression(between.getLeftExpression()));
            resultMap.put(BETWEEN_START, getExpression(between.getBetweenExpressionStart()));
            resultMap.put(BETWEEN_END, getExpression(between.getBetweenExpressionEnd()));
        } else if (expression instanceof BinaryExpression) {
            BinaryExpression binaryExpression = (BinaryExpression) expression;
            Expression leftExpression = binaryExpression.getLeftExpression();
            Expression rightExpression = binaryExpression.getRightExpression();
            String stringExpression = binaryExpression.getStringExpression();
            resultMap.put(NODE, OPERATION);
            resultMap.put(LEFT_EXPRESSION, getExpression(leftExpression));
            resultMap.put(RIGHT_EXPRESSION, getExpression(rightExpression));
            resultMap.put("operator", stringExpression);
        } else if (expression instanceof Parenthesis) {
            Parenthesis parenthesis = (Parenthesis) expression;
            return getExpression(parenthesis.getExpression());
        } else if (expression instanceof InExpression) {
            InExpression inExpression = (InExpression) expression;
            resultMap.put(NODE, IN_EXPRESSION);
            resultMap.put(LEFT_EXPRESSION, getExpression(inExpression.getLeftExpression()));
            if (StringUtil.isNotEmpty(inExpression.getRightItemsList())) {
                resultMap.put(RIGHT_EXPRESSION, getItemsList(inExpression.getRightItemsList()));
            }
            if (StringUtil.isNotEmpty(inExpression.getRightExpression())) {
                resultMap.put(RIGHT_EXPRESSION, getExpression(inExpression.getRightExpression()));
            }
        } else {
            log.warn("Not Support This Expression: {}", expression);
            resultMap.put(NODE, VALUE_U);
            resultMap.put(VALUE_L, expression.toString());
        }
        return resultMap;
    }

    private static Map getItemsList(ItemsList itemsList) {
        Map<String, Object> map = Maps.newHashMap();
        if (itemsList instanceof SubSelect) {
            map.put(NODE, SUB_SELECT);
            map.put(VALUE_L, itemsList.toString());
        } else if (itemsList instanceof ExpressionList) {
            ExpressionList expressionList = (ExpressionList) itemsList;
            List<Expression> expressions = expressionList.getExpressions();
            Map<String, Object> parameters = getParameters(expressions);
            map.put(PARAMETERS, parameters);
        } else {
            throw new RuntimeException("Parse ItemsList Error, Not Support This.");
        }
        return map;
    }

    private static Map<String, Object> getParameters(List<Expression> parameters) {
        List<Map<String, Object>> list = Lists.newArrayList();
        for (Expression parameter : parameters) {
            list.add(getExpression(parameter));
        }
        Map<String, Object> map = Maps.newHashMap();
        map.put("expressions", list);
        return map;
    }

    private static final String SELECT = "select";
    private static final String FROM = "from";
    private static final String WHERE = "where";
    private static final String GROUP_BY = "groupBy";
    private static final String HAVING = "having";
    private static final String ORDER_BY = "orderBy";
    private static final String LIMIT = "limit";

    private static final String CONNECTOR = "connector";

    private static final String NODE = "node";
    private static final String COLUMN = "Column";
    private static final String VALUE_U = "Value";
    private static final String VALUE_L = "value";
    private static final String TABLE = "Table";
    private static final String FUNCTION = "Function";
    private static final String OPERATION = "Operation";
    private static final String AND_EXPRESSION = "AndExpression";
    private static final String OR_EXPRESSION = "OrExpression";
    private static final String IN_EXPRESSION = "InExpression";
    private static final String RIGHT_EXPRESSION = "rightExpression";
    private static final String LEFT_EXPRESSION = "leftExpression";
    private static final String BETWEEN_START = "betweenStart";
    private static final String BETWEEN_END = "betweenEnd";
    private static final String SUB_SELECT = "SubSelect";
    private static final String BETWEEN = "Between";
    private static final String ITEM = "item";
    private static final String NAME = "name";
    private static final String EXPRESSION = "expression";
    private static final String PARAMETERS = "parameters";
    private static final String ALIAS = "alias";
    private static final String USEAS = "useAs";
    private static final Map<String, String> typeMap = Maps.newHashMap();


    private static final String SCHEMA_INT = "int";
    private static final String SCHEMA_LONG = "long";
    private static final String SCHEMA_FLOAD = "fload";
    private static final String SCHEMA_DOUBLE = "double";
    private static final String SCHEMA_STRING = "string";
    private static final String SCHEMA_DATE = "date";
    private static final String SCHEMA_TIMESTAMP = "timestamp";

    static {
        typeMap.put("INT8", SCHEMA_INT);
        typeMap.put("INT16", SCHEMA_INT);
        typeMap.put("INT32", SCHEMA_INT);
        typeMap.put("INT64", SCHEMA_LONG);
        typeMap.put("BIGINT", SCHEMA_LONG);
        typeMap.put("UINT8", SCHEMA_INT);
        typeMap.put("UINT16", SCHEMA_INT);
        typeMap.put("UINT32", SCHEMA_LONG);
        typeMap.put("UINT64", SCHEMA_LONG);
        typeMap.put("FLOAT32", SCHEMA_FLOAD);
        typeMap.put("FLOAT64", SCHEMA_DOUBLE);
        typeMap.put("VARCHAR", SCHEMA_STRING);
        typeMap.put("STRING", SCHEMA_STRING);
        typeMap.put("DATE", SCHEMA_DATE);
        typeMap.put("DATETIME", SCHEMA_TIMESTAMP);
    }

    @Data
    @NoArgsConstructor
    static class SQLStructure {
        private List<Map<String, Object>> select;
        private List<Map<String, Object>> from;
        private Map<String, Object> where;
        private Map<String, Object> having;
        private List<Map<String, Object>> groupBy;
        private List<Map<String, Object>> orderBy;
        private Map<String, Object> limit;
    }
}
