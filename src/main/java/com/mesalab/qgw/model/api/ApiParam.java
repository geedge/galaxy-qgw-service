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
    private String dialectDBType; //方言数据库类型，用于SQL格式解析校验
    private String reportId;//离线统计报告唯一ID
    private SQLQuerySource sqlQuerySource = new SQLQuerySource();

}
