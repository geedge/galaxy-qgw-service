package com.mesalab.common.utils.sqlparser;

import com.mesalab.qgw.dialect.ClickHouseDialect;
import com.mesalab.qgw.dialect.DruidDialect;
import com.mesalab.qgw.model.basic.SelectStatement;
import com.mesalab.qgw.model.basic.udf.TIME_FLOOR_WITH_FILL;
import com.mesalab.qgw.model.basic.udf.DATETIME_FLOOR_WITH_FILL;
import com.mesalab.qgw.model.basic.udf.UDF;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.SelectItem;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TODO
 *
 * @Classname QueryTypeHelper
 * @Date 2024/3/11 14:43
 * @Author wWei
 */
public class QueryTypeHelper {

    private QueryTypeHelper() {
    }

    public static String determineQueryType(SelectStatement dbSelectStatement) {
        Set<UDF> udfSet = dbSelectStatement.getUdfSet();
        if (udfSet != null && !udfSet.isEmpty()) {
            for (UDF udf : udfSet) {
                if (udf instanceof TIME_FLOOR_WITH_FILL
                        || udf instanceof DATETIME_FLOOR_WITH_FILL) {
                    return ExampleDataHelper.QUERY_TYPE_TIMESERIES;
                }
            }
        }

        Map<String, String> groupDimension = dbSelectStatement.getGroupDimension();
        if (groupDimension != null && !groupDimension.isEmpty()) {
            return ExampleDataHelper.QUERY_TYPE_GROUP_BY;
        }

        List<SelectItem<?>> selectItems = dbSelectStatement.getSelectItems();
        if (selectItems != null && !selectItems.isEmpty()) {
            for (SelectItem<?> selectItem : selectItems) {
                Expression expression = selectItem.getExpression();
                if (expression instanceof Function) {
                    Function function = (Function) expression;
                    if (SQLFunctionUtil.pAggregateFunStandard.matcher(function.toString()).find()
                            || ClickHouseDialect.pAggregateFunSpecificOfCK.matcher(function.toString()).find()
                            || DruidDialect.pAggregateFunSpecificOfDruid.matcher(function.toString()).find()) {
                        return ExampleDataHelper.QUERY_TYPE_AGG_STATISTICS;
                    }
                }
            }
        }
        return ExampleDataHelper.QUERY_TYPE_OTHER;
    }

    public static Object determineParam(SelectStatement dbSelectStatement, String queryType) {
        if (ExampleDataHelper.QUERY_TYPE_TIMESERIES.equals(queryType)) {
            TIME_FLOOR_WITH_FILL udfElements = dbSelectStatement.getUdfSet().stream()
                    .filter(udf -> udf instanceof TIME_FLOOR_WITH_FILL)
                    .map(udf -> (TIME_FLOOR_WITH_FILL) udf)
                    .findFirst()
                    .orElse(null);

            if (udfElements != null) {
                String name = udfElements.getName();
                String timestamp = udfElements.getTimestamp();
                Map<String, String> groupDimension = dbSelectStatement.getGroupDimension();
                for (String groupKey : groupDimension.keySet()) {
                    if (groupDimension.get(groupKey).contains(name) && groupDimension.get(groupKey).contains(timestamp)) {
                        return groupKey;
                    }
                }
            }

            DATETIME_FLOOR_WITH_FILL datetimeUdfElements = dbSelectStatement.getUdfSet().stream()
                    .filter(udf -> udf instanceof DATETIME_FLOOR_WITH_FILL)
                    .map(udf -> (DATETIME_FLOOR_WITH_FILL) udf)
                    .findFirst()
                    .orElse(null);

            if (datetimeUdfElements != null) {
                String name = datetimeUdfElements.getName();
                String timestamp = datetimeUdfElements.getTimestamp();
                Map<String, String> groupDimension = dbSelectStatement.getGroupDimension();
                for (String groupKey : groupDimension.keySet()) {
                    if (groupDimension.get(groupKey).contains(name) && groupDimension.get(groupKey).contains(timestamp)) {
                        return groupKey;
                    }
                }
            }

        } else if (ExampleDataHelper.QUERY_TYPE_GROUP_BY.equals(queryType)) {
            List<OrderByElement> orderByElements = dbSelectStatement.getOrderByElements();
            if (orderByElements != null && !orderByElements.isEmpty()) {
                return !orderByElements.get(0).isAsc();
            } else {
                return false;
            }
        }

        return null;
    }
}
