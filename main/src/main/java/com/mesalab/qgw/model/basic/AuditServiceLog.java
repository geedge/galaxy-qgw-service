package com.mesalab.qgw.model.basic;

import lombok.Data;


@Data
public class AuditServiceLog {

    private String annotation;

    private String requestId;

    private String cacheKey;

    private String remoteAddr;

    private String clientIp;

    private long exeTime;

    private String dbType;

    private String param;

    private String url;

    private String method;

    private String classMethod;







}
