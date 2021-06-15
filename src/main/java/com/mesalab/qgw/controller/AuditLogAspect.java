package com.mesalab.qgw.controller;

import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.collect.Maps;
import com.google.protobuf.Api;
import com.google.protobuf.Option;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.utils.QueryCacheUtils;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.common.utils.SQLHelper;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.AuditLog;
import com.mesalab.qgw.model.api.AuditServiceLog;
import com.mysql.cj.util.StringUtils;
import com.zdjizhi.utils.IPUtil;
import com.zdjizhi.utils.StringUtil;
import org.apache.commons.lang3.StringEscapeUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;


@Aspect
@Component
public class AuditLogAspect {

    private ThreadLocal<Long> startTime = ThreadUtil.createThreadLocal(false);
    private ThreadLocal<AuditServiceLog> auditLog = ThreadUtil.createThreadLocal(false);
    private static final Log log = LogFactory.get();


    @Pointcut("@annotation(com.mesalab.qgw.model.api.AuditLog)")
    public void annotationPointCut(){}

    @Before("annotationPointCut()")
    public void doBefore(JoinPoint joinPoint) {

        startTime.set(System.currentTimeMillis());
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes.getRequest();
        String url = request.getRequestURL().toString();
        String method = request.getMethod();
        String remoteAddr = request.getRemoteAddr();
        String clientIp = IPUtil.getIpAddr(request);
        String classMethod = joinPoint.getSignature().getDeclaringTypeName() + "." + joinPoint.getSignature().getName();
        Object paramObject = joinPoint.getArgs()[0];

        MethodSignature sign =  (MethodSignature)joinPoint.getSignature();
        Method method2 = sign.getMethod();
        AuditLog annotation = method2.getAnnotation(AuditLog.class);
        AuditServiceLog auditServiceLog =  new AuditServiceLog();
        auditServiceLog.setAnnotation(annotation.value());
        auditServiceLog.setRemoteAddr(remoteAddr);
        auditServiceLog.setClientIp(clientIp);
        auditServiceLog.setMethod(method);
        if (log.isDebugEnabled()) {
            auditServiceLog.setParam(paramObject.toString());
            auditServiceLog.setUrl(url);
            auditServiceLog.setClassMethod(classMethod);
        }

        auditServiceLog.setCacheKey(DigestUtil.md5Hex(Optional.ofNullable(StringEscapeUtils.escapeHtml4(request.getQueryString())).orElse("")));

        if (paramObject instanceof ApiParam) {
            ApiParam  param = (ApiParam) paramObject;
            auditServiceLog.setQueryKey(SQLHelper.getQueryId(param.getResultId(), param.getQuery()));
        }



        auditLog.set(auditServiceLog);
    }

    @AfterThrowing(value = "annotationPointCut()", throwing = "exception")
    public void doAfterThrowingAdvice(JoinPoint joinPoint, Throwable exception){
        AuditServiceLog logAfter =  auditLog.get();
        logAfter.setQueryKey("");
        logAfter.setParam(Arrays.toString(joinPoint.getArgs()));
        log.error("Audit Log [error]:{}", JsonMapper.toJsonString(logAfter));

    }


    @After(value = "annotationPointCut()")
    public void after(JoinPoint joinPoint) {
       if (joinPoint.getArgs()[0] instanceof ApiParam) {
           ApiParam param = (ApiParam) joinPoint.getArgs()[0];
           auditLog.get().setDbType(param.getDbType());
       }


    }



    @AfterReturning(returning = "result", pointcut = "annotationPointCut()")
    public void doAfterReturning(Object result) {
        AuditServiceLog logAfter =  auditLog.get();
        if (StringUtil.isNotEmpty(result)) {
            QueryCacheUtils.put(logAfter.getCacheKey(), result);
        }
        if(result instanceof BaseResult){
            BaseResult baseResult = (BaseResult) result;
            baseResult.setQueryKey(logAfter.getQueryKey());
        }
        Long exeTime = System.currentTimeMillis() - startTime.get();
        logAfter.setExeTime(exeTime);

        log.info("Audit Log [completed]:{}", JsonMapper.toJsonString(logAfter));

    }



}
