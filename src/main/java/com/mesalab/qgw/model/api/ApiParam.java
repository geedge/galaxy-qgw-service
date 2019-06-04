package com.mesalab.qgw.model.api;

import lombok.Data;

import java.io.Serializable;
@Data
public class ApiParam implements Serializable {

    private String query;
    private String option;
    private String format;
    private String schema;
    private String tableName;
    private String dbType;// 数据库类型
    private String dialectDBType; //实际解析方言数据库类型
    private SQLQuerySource sqlQuerySource = new SQLQuerySource();

}
