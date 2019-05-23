package com.mesalab.qgw.model.api;

import lombok.Data;

import java.io.Serializable;

@Data
public class DruidIoHttpSource implements Serializable {
    private  String url;
    private String dbname;
    private String sqlTimeZone;
    private String skipEmptyBuckets = "false";//汇总结果包含0
    private long  timeout = 600000;

}
