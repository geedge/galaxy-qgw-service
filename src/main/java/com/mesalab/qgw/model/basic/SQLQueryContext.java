package com.mesalab.qgw.model.basic;

import com.geedgenetworks.utils.StringUtil;
import lombok.*;

import java.io.Serializable;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SQLQueryContext implements Serializable {

    private String originalSQL;
    private String option;
    private Boolean sampled;
    private String format;
    private String queryId;
    private String resultId;
    private Integer timeout;
    private String dbEngine;
    private String sqlDialect; // SQL Dialect,such as: Mysql,Hive, etc. It used syntax and semantic rules to transform SQL query.
    private SelectStatement federationSelectStatement;
    private SelectStatement dbSelectStatement;
    public String getResultId() {
        return resultId;
    }

    public boolean originalSQLIsNull() {
        return StringUtil.isBlank(originalSQL);
    }

    public void setResultId(String resultId) {
        this.resultId = resultId;
    }
}
