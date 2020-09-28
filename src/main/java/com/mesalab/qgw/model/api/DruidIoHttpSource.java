package com.mesalab.qgw.model.api;

import lombok.Data;

import java.io.Serializable;

@Data
public class DruidIoHttpSource implements Serializable {
    private  String url;
    private String dbname;
    private String skipEmptyBuckets = "true";
}
