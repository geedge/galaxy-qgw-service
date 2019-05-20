package com.mesalab.qgw.model.api;

import lombok.Data;

@Data
public class SQLQuerySource {
    private String tableName;
    private String selectItems;
    private String expr;
    private String groupByClause;
    private String orderBy;
    private String limit;
}
