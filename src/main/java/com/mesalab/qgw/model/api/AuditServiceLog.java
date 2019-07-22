package com.mesalab.qgw.model.api;

import lombok.Data;


@Data
public class AuditServiceLog {

    private String annotation;

    private String clientIp;

    private long exeTime;

    private String remoteAddr;

    private String param;

    private String url;

    private String method;

    private String classMethod;

    private String queryKey;

    private String dbType;






}
