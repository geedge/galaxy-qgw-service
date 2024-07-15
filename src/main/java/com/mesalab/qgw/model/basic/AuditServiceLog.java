package com.mesalab.qgw.model.basic;

import lombok.Data;


@Data
public class AuditServiceLog {

    private String annotation;

    private String requestId;

    private String cacheKey;

    private String remoteAddr;

    private String clientIp;

    private long elapsed;

    private String dbEngine;

    private String requestParam;

    private String url;

    private String method;

    private String classMethod;







}
