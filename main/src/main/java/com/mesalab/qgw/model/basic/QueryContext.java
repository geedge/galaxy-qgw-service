package com.mesalab.qgw.model.basic;

import com.geedgenetworks.utils.StringUtil;
import lombok.*;

import java.io.Serializable;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryContext implements Serializable {

    private String query;
    private String option;
    private Boolean sampled;
    private String format;
    private String queryId;
    private String resultId;
    private Integer timeout;
    private String dbEngine;
    // SQL Dialect,such as: Mysql,Hive, etc. It used syntax and semantic rules to transform SQL query.
    private String sqlDialect;
    private SQLQuerySource engineQuerySource;
    private SQLQuerySource dbQuerySource;

    public String getResultId() {
        return resultId;
    }

    public boolean queryParamIsNull() {
        return StringUtil.isBlank(query);
    }

    public void setResultId(String resultId) {
        this.resultId = resultId;
    }
}
