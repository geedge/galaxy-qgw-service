package com.mesalab.qgw.model.job;

import lombok.Data;

@Data
public class ExecutorParam {

    private String server;
    private String datasource;
    private String username;
    private String password;
    private String tablename;
    private int maxdays;
}
