package com.mesalab.common.utils.sqlparser;

import com.google.common.collect.Maps;
import com.mesalab.qgw.constant.DataTypeConst;
import lombok.Data;
import lombok.SneakyThrows;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
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

    private SelectItemHelper() {
    }


    public static Map<String, AliasObject> getSelectItem(String sql) throws JSQLParserException {
        Select select = (Select) CCJSqlParserUtil.parse(sql);
        SelectAdapter selectAdapter = new SelectAdapter();
        select.getSelectBody().accept(selectAdapter);
        return selectAdapter.getAliasObjects();
    }

    private static class SelectAdapter extends SelectVisitorAdapter {

        private final Map<String, AliasObject> aliasObjects = Maps.newHashMap();

        public Map<String, AliasObject> getAliasObjects() {
            return aliasObjects;
        }

        @SneakyThrows
        @Override
        public void visit(PlainSelect plainSelect) {
            List<SelectItem> selectItems = plainSelect.getSelectItems();
            if (selectItems == null) {
                return;
            }
            for (int i = 0; i < selectItems.size(); i++) {
                SelectItem selectItem = selectItems.get(i);
                SelectItemAdapter selectItemAdapter = new SelectItemAdapter();
                selectItem.accept(selectItemAdapter);
                AliasObject aliasExpr = selectItemAdapter.getAliasExpr();
                if (aliasExpr != null) {
                    aliasExpr.setIndex(i);
                    this.aliasObjects.put(aliasExpr.getName(), aliasExpr);
                }
            }
        }

        @Override
        public void visit(SetOperationList setOpList) {
            SelectBody selectBody = setOpList.getSelects().get(0);
            if (selectBody instanceof PlainSelect) {
                selectBody.accept(this);
            }
        }
    }

    private static class SelectItemAdapter extends SelectItemVisitorAdapter {

        private AliasObject aliasExpr = new AliasObject();

        public AliasObject getAliasExpr() {
            return aliasExpr;
        }

        @Override
        public void visit(SelectExpressionItem selectExpressionItem) {
            Expression expression = selectExpressionItem.getExpression();
            String name = selectExpressionItem.getAlias() != null ? selectExpressionItem.getAlias().getName() : selectExpressionItem.toString();
            name = SQLHelper.removeQuotesAndBackticks(name);
            if (expression instanceof Column) {
                Column column = (Column) expression;
                AliasColumn aliasColumn = new AliasColumn();
                aliasColumn.setName(name);
                aliasColumn.setFieldName(column.getColumnName());
                this.aliasExpr = aliasColumn;
                return;
            } else if (expression instanceof Function) {
                Function function = (Function) expression;
                String functionName = function.getName().toUpperCase();
                Map<String, String> functionDateTypeMap = Maps.newHashMap();
                functionDateTypeMap.put("FROM_UNIXTIME", DataTypeConst.TIMESTAMP_FORMAT);
                functionDateTypeMap.put("DATE_FORMAT", DataTypeConst.TIMESTAMP_FORMAT);
                functionDateTypeMap.put("CONVERT_TZ", DataTypeConst.TIMESTAMP_FORMAT);
                functionDateTypeMap.put("TIME_FORMAT", DataTypeConst.TIMESTAMP_FORMAT);
                functionDateTypeMap.put("FROM_UNIXTIME_MILLIS", DataTypeConst.TIMESTAMP_MS_FORMAT);
                functionDateTypeMap.put("UNIX_TIMESTAMP", DataTypeConst.UNIX_TIMESTAMP);
                functionDateTypeMap.put("TIME_FLOOR_WITH_FILL", DataTypeConst.UNIX_TIMESTAMP);
                functionDateTypeMap.put("UNIX_TIMESTAMP_MILLIS", DataTypeConst.UNIX_TIMESTAMP_MS);
                functionDateTypeMap.put("PERCENTILES_HDR", DataTypeConst.HDR_HISTOGRAM);
                functionDateTypeMap.put("IP_TO_GEO", DataTypeConst.IP);
                functionDateTypeMap.put("IP_TO_CITY", DataTypeConst.IP);
                functionDateTypeMap.put("IP_TO_COUNTRY", DataTypeConst.IP);
                functionDateTypeMap.put("IP_TO_ISP", DataTypeConst.IP);
                functionDateTypeMap.put("IP_TO_ASN", DataTypeConst.IP);
                functionDateTypeMap.put("IP_TO_ASN_DETAIL", DataTypeConst.IP);
                functionDateTypeMap.put("IP_TO_ASN_ORG", DataTypeConst.IP);

                if (functionDateTypeMap.containsKey(functionName)) {
                    AliasFunExpr aliasFunExpr = new AliasFunExpr();
                    aliasFunExpr.setName(name);
                    aliasFunExpr.setDateType(functionDateTypeMap.get(functionName));
                    this.aliasExpr = aliasFunExpr;
                    return;
                }
            }
            AliasObject aliasObject = new AliasObject();
            aliasObject.setName(name);
            this.aliasExpr = aliasObject;
        }

    }

    @Data
    public static class AliasObject {
        private String name;
        private int index;
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
