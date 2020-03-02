package com.mesalab.qgw.model.api;

import lombok.Data;

@Data
public class ClickHouseSubqueryParam {
    private String indexTableFullName;
    private Boolean isValidIndexTable;
}
