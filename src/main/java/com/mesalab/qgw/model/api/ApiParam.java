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
    private String resultId;//离线统计报告结果ID
    private String sql;//当前执行的SQL
    private SQLQuerySource engineQuerySource; //网关引擎处理SQL对象
    private SQLQuerySource dbQuerySource; //下沉数据源引擎的SQL对象


}
