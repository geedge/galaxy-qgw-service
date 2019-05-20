package com.mesalab.qgw.model.api;

import lombok.Data;

import java.io.Serializable;
@Data
public class QueryParam implements Serializable {

    private String query;
    private String option;
    private String format;
    private String schema;
    private String tableName;

}
