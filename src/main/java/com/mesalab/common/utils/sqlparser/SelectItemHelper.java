package com.mesalab.common.utils.sqlparser;

import com.google.common.collect.Maps;
import com.mesalab.qgw.constant.DataTypeConst;
import lombok.Data;
import lombok.SneakyThrows;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.RowConstructor;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.*;

import java.util.*;

/**
 * TODO
 *
 * @Classname SelectItemHelper
 * @Date 2024/2/27 09:48
 * @Author wWei
 */
public class SelectItemHelper {

    private static final Map<String, String> FUNCTION_DATE_TYPE_MAPPING = Maps.newHashMap();

    static {
        FUNCTION_DATE_TYPE_MAPPING.put("FROM_UNIXTIME", DataTypeConst.TIMESTAMP_FORMAT);
        FUNCTION_DATE_TYPE_MAPPING.put("DATE_FORMAT", DataTypeConst.TIMESTAMP_FORMAT);
        FUNCTION_DATE_TYPE_MAPPING.put("CONVERT_TZ", DataTypeConst.TIMESTAMP_FORMAT);
        FUNCTION_DATE_TYPE_MAPPING.put("TIME_FORMAT", DataTypeConst.TIMESTAMP_FORMAT);
        FUNCTION_DATE_TYPE_MAPPING.put("FROM_UNIXTIME_MILLIS", DataTypeConst.TIMESTAMP_MS_FORMAT);
        FUNCTION_DATE_TYPE_MAPPING.put("UNIX_TIMESTAMP", DataTypeConst.UNIX_TIMESTAMP);
        FUNCTION_DATE_TYPE_MAPPING.put("TIME_FLOOR_WITH_FILL", DataTypeConst.UNIX_TIMESTAMP);
        FUNCTION_DATE_TYPE_MAPPING.put("UNIX_TIMESTAMP_MILLIS", DataTypeConst.UNIX_TIMESTAMP_MS);
        FUNCTION_DATE_TYPE_MAPPING.put("PERCENTILES_HDR", DataTypeConst.HDR_HISTOGRAM);
        FUNCTION_DATE_TYPE_MAPPING.put("IP_TO_GEO", DataTypeConst.IP);
        FUNCTION_DATE_TYPE_MAPPING.put("IP_TO_CITY", DataTypeConst.IP);
        FUNCTION_DATE_TYPE_MAPPING.put("IP_TO_COUNTRY", DataTypeConst.IP);
        FUNCTION_DATE_TYPE_MAPPING.put("IP_TO_ISP", DataTypeConst.IP);
        FUNCTION_DATE_TYPE_MAPPING.put("IP_TO_ASN", DataTypeConst.IP);
        FUNCTION_DATE_TYPE_MAPPING.put("IP_TO_ASN_DETAIL", DataTypeConst.IP);
        FUNCTION_DATE_TYPE_MAPPING.put("IP_TO_ASN_ORG", DataTypeConst.IP);
    }

    private SelectItemHelper() {
    }


    public static Map<String, AliasObject> getSelectItem(String sql) throws JSQLParserException {
        Select select = (Select) CCJSqlParserUtil.parse(sql);
        SelectAdapter selectAdapter = new SelectAdapter();
        select.accept(selectAdapter, null);
        return selectAdapter.getAliasObjects();
    }

    private static class SelectAdapter extends SelectVisitorAdapter<Object> {

        private final Map<String, AliasObject> aliasObjects = Maps.newHashMap();

        public Map<String, AliasObject> getAliasObjects() {
            return aliasObjects;
        }

        @SneakyThrows
        @Override
        public Object visit(PlainSelect plainSelect, Object context) {
            List<SelectItem<?>> selectItems = plainSelect.getSelectItems();
            if (selectItems == null) {
                return null;
            }
            for (int i = 0; i < selectItems.size(); i++) {
                SelectItem<?> selectItem = selectItems.get(i);
                SelectItemAdapter selectItemAdapter = new SelectItemAdapter();
                selectItem.accept(selectItemAdapter, null);
                AliasObject aliasExpr = selectItemAdapter.getAliasExpr();
                if (aliasExpr != null) {
                    aliasExpr.setIndex(i);
                    this.aliasObjects.put(aliasExpr.getName(), aliasExpr);
                }
            }
            return null;
        }

        @Override
        public Object visit(SetOperationList setOpList, Object context) {
            Select select = setOpList.getSelects().get(0);
            if (select instanceof PlainSelect) {
                select.accept(this, null);
            }
            return null;
        }
    }

    private static class SelectItemAdapter extends SelectItemVisitorAdapter<Object> {

        private AliasObject aliasExpr = new AliasObject();

        public AliasObject getAliasExpr() {
            return aliasExpr;
        }

        @Override
        public Object visit(SelectItem selectExpressionItem, Object context) {
            Expression expression = selectExpressionItem.getExpression();
            String name = selectExpressionItem.getAlias() != null ? selectExpressionItem.getAlias().getName() : selectExpressionItem.toString();
            name = SQLHelper.removeQuotesAndBackticks(name);
            if (expression instanceof Column) {
                Column column = (Column) expression;
                AliasColumn aliasColumn = new AliasColumn();
                aliasColumn.setName(name);
                aliasColumn.setFieldName(column.getColumnName());
                this.aliasExpr = aliasColumn;
                return null;
            } else if (expression instanceof Function) {
                Function function = (Function) expression;
                String functionName = function.getName().toUpperCase();
                if (SQLFunctionUtil.GROUP_UNIQ_ARRAY.equalsIgnoreCase(functionName)
                        && function.getParameters() != null
                        && !function.getParameters().isEmpty()) {
                    ExpressionList<?> paramExpressions = function.getParameters();
                    Expression expr1 = paramExpressions.get(0);
                    if (expr1 instanceof Column) {
                        Column column = (Column) expr1;
                        AliasColumn aliasColumn = new AliasColumn();
                        aliasColumn.setName(name);
                        aliasColumn.setFieldName(column.getColumnName());
                        aliasColumn.setArray(true);
                        this.aliasExpr = aliasColumn;
                        return null;
                    } else if ((expr1 instanceof ParenthesedExpressionList) && ((ParenthesedExpressionList) expr1).get(0) instanceof Column) {
                        Column column = (Column) ((ParenthesedExpressionList) expr1).get(0);
                        AliasColumn aliasColumn = new AliasColumn();
                        aliasColumn.setName(name);
                        aliasColumn.setFieldName(column.getColumnName());
                        aliasColumn.setArray(true);
                        this.aliasExpr = aliasColumn;
                        return null;
                    }/* else if (expr1 instanceof RowConstructor
                            && ((RowConstructor) expr1).getExprList() != null
                            && ((RowConstructor) expr1).getExprList().getExpressions().get(0) instanceof Column) {
                        AliasColumn aliasColumn = new AliasColumn();
                        aliasColumn.setName(name);
                        aliasColumn.setArray(true);
                        this.aliasExpr = aliasColumn;
                        return null;
                    }*/
                }
                if (FUNCTION_DATE_TYPE_MAPPING.containsKey(functionName)) {
                    AliasFunExpr aliasFunExpr = new AliasFunExpr();
                    aliasFunExpr.setName(name);
                    aliasFunExpr.setDateType(FUNCTION_DATE_TYPE_MAPPING.get(functionName));
                    this.aliasExpr = aliasFunExpr;
                    return null;
                }
                AliasObject aliasObject = new AliasObject();
                aliasObject.setName(name);
                this.aliasExpr = aliasObject;
            }
            return null;
        }
    }

    @Data
    public static class AliasObject {
        private String name;
        private int index;
        private boolean isArray;
    }

    @Data
    public static class AliasColumn extends AliasObject {
        private String fieldName;
    }

    @Data
    public static class AliasFunExpr extends AliasObject {
        private String dateType;
    }
}
