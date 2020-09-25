package com.mesalab.qgw.model.api;

import lombok.Data;

import java.io.Serializable;
@Data
public class HBaseHttpSource implements Serializable {

    private String url;
    private String dbName;
    private String tableName;
    private String columnFamily;
    private String columnName;

}
