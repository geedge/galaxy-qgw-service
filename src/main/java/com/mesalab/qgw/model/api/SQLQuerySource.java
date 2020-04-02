package com.mesalab.qgw.model.api;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mesalab.qgw.model.api.udf.UDF;
import com.zdjizhi.utils.StringUtil;
import lombok.Data;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.select.GroupByElement;
import net.sf.jsqlparser.statement.select.OrderByElement;

import java.util.List;
import java.util.Map;

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
    private Expression whereExpression; /** where 对象 **/
    private GroupByElement groupByElement;
    private List<OrderByElement> listOrderElement;
    private Map<String, String> aliasFields= Maps.newHashMap();
    private List<UDF> udfList = Lists.newArrayList();
    private Map<String, String> groupDimension = Maps.newHashMap();
    private PartitionTimeRange partitionTimeRange = new PartitionTimeRange();
    @Data
    public class PartitionTimeRange {
        private Expression left;
        private Expression right;
        private Expression equal;

    }

    public Map<String, String> getGroupDimension() {
        if (groupByElement == null) {
            return null;
        }
        for (Expression groupByExpression : groupByElement.getGroupByExpressions()) {

            if (StringUtil.isNotBlank(aliasFields.get(groupByExpression.toString()))) { //别名
                groupDimension.put(groupByExpression.toString(), aliasFields.get(groupByExpression.toString()));
            } else {
                //非别名，确定实际的别名
                for (String key : aliasFields.keySet()) {
                    if (aliasFields.get(key).equalsIgnoreCase(groupByExpression.toString())) {
                        groupDimension.put(key, groupByExpression.toString());
                        break;
                    }
                }
            }

        }
        return groupDimension;
    }















}
