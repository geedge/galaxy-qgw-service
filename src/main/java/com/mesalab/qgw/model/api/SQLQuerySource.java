package com.mesalab.qgw.model.api;

import com.google.common.collect.Maps;
import lombok.Data;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.select.OrderByElement;

import java.util.List;
import java.util.Map;

@Data
public class SQLQuerySource {
    private String tableName;
    private String fromItem;
    private String selectItems;
    private String expr;
    private String groupByClause;
    private String orderBy;
    private String limit;
    private Expression expression;
    private List<OrderByElement> listOrderElement;
    private Map<String, String> fields = Maps.newHashMap();

}
