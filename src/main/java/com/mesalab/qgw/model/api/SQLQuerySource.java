package com.mesalab.qgw.model.api;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mesalab.qgw.model.api.udf.UDF;
import com.zdjizhi.utils.StringUtil;
import lombok.Data;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.select.GroupByElement;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.SubSelect;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SQL Parser properties
 */
@Data
public class SQLQuerySource {
    private String tableName;
    private String partitionKey;/** 分区键，多用于检索查询首要过滤列；例如timestamp **/
    private String fromItem;
    private String selectItems;
    private String expr;
    private String orderBy;
    private String limit;
    private SubSelect subSelect;
    private Object executeSQLSource;
    private Expression whereExpression; /** where 对象 **/
    private GroupByElement groupByElement;
    private List<OrderByElement> listOrderElement;
    private Map<String, String> aliasFields= Maps.newHashMap();/** k=alias,v=column **/
    private Set<UDF> udfSet = Sets.newHashSet();
    private Map<String, String> groupDimension = Maps.newHashMap();


    public Map<String, String> getGroupDimension() {
        if (groupByElement == null) {
            return null;
        }
        for (Expression groupByExpression : groupByElement.getGroupByExpressions()) {

            String groupBy = groupByExpression.toString();
            if (groupBy.startsWith("\"") && groupBy.endsWith("\"")) {
                groupBy = groupBy.substring(1, groupBy.length() - 1);
            }
            if (StringUtil.isNotBlank(aliasFields.get(groupBy))) {
                groupDimension.put(groupBy, aliasFields.get(groupBy));
            } else {
                //非别名，确定实际的别名
                for (String key : aliasFields.keySet()) {
                    if (aliasFields.get(key).equalsIgnoreCase(groupBy)) {
                        groupDimension.put(key, groupBy);
                        break;
                    }
                }
            }

        }
        return groupDimension;
    }















}
