package com.mesalab.qgw.model.basic;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mesalab.common.utils.sqlparser.SQLHelper;
import com.mesalab.qgw.model.basic.udf.UDF;
import com.geedgenetworks.utils.StringUtil;
import lombok.Data;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.select.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A parsed SQL statement
 */
@Data
public class SelectStatement {
    private String execSQL;
    private String sqlBody;
    private List<String> tableNames = Lists.newArrayList();
    private String partitionKey;
    private List<Join> joins;
    private FromItem fromItem;
    private List<SelectItem> selectItems;
    private Map<String, String> aliasFields = Maps.newHashMap();
    private Expression whereExpression;
    private GroupByElement groupByElement;
    private List<OrderByElement> orderByElements;
    private Map<String, String> groupDimension = Maps.newHashMap();
    private String limit;
    private boolean isEnableLimit;

    private SubSelect subSelect;
    private List<SelectStatement> subSqlQuerySources = Lists.newArrayList();
    private Set<UDF> udfSet = Sets.newHashSet();

    public boolean isEnableLimit() {
        return !SQLHelper.INVALID_LIMIT_DESC.equalsIgnoreCase(limit);
    }

    public String getSelect() {
        return Joiner.on(",").join(this.selectItems);
    }

    public String getOrderBy() {
        return Joiner.on(",").join(this.orderByElements);
    }
    public Map<String, String> getGroupDimension() {
        if (groupByElement == null) {
            return null;
        }
        groupBy:
        for (Expression groupByExpression : groupByElement.getGroupByExpressions()) {

            String groupBy = groupByExpression.toString();
            groupBy = SQLHelper.removeQuotesAndBackticks(groupBy);
            if (StringUtil.isNotBlank(aliasFields.get(groupBy))) {
                groupDimension.put(groupBy, aliasFields.get(groupBy));
            } else {
                //非别名，确定实际的别名
                for (String key : aliasFields.keySet()) {
                    if (aliasFields.get(key).equalsIgnoreCase(groupBy)) {
                        groupDimension.put(key, groupBy);
                        continue groupBy;
                    }
                }
                groupDimension.put(groupBy, groupBy);
            }

        }
        return groupDimension;
    }
}
