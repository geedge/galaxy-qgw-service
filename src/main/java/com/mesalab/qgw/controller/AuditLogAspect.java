package com.mesalab.qgw.controller;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.utils.QueryCacheUtils;
import com.mesalab.common.utils.IPUtil;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.AuditLog;
import com.mesalab.qgw.model.api.AuditServiceLog;
import com.zdjizhi.utils.StringUtil;
import org.apache.commons.codec.digest.DigestUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Arrays;


@Aspect
@Component
public class AuditLogAspect {
    private static final String UNKNOWN_IPV4_ADDRESS = "0.0.0.0";
    private static final String LOCAL_IPV6_ADDRESS = "0:0:0:0:0:0:0:1";
    private static final String LOCALHOST_IPV4_ADDRESS = "127.0.0.1";
    private static final String LOCALHOST_NAME = "localhost";
    ThreadLocal<Long> startTime = new ThreadLocal<>();
    ThreadLocal<AuditServiceLog> auditLog = new ThreadLocal<>();
    private static final Log log = LogFactory.get();


    @Pointcut("@annotation(com.mesalab.qgw.model.api.AuditLog)")
    public void annotationPointCut(){}

    @Before("annotationPointCut()")
    public void doBefore(JoinPoint joinPoint) throws Throwable {
        // 接收到请求，记录请求内容
        startTime.set(System.currentTimeMillis());
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes.getRequest();
        String url = request.getRequestURL().toString();
        String method = request.getMethod();
        String remoteAddr = request.getRemoteAddr();
        String clientIp = IPUtil.parseIP(request);

        if(UNKNOWN_IPV4_ADDRESS.equals(clientIp)
                || LOCAL_IPV6_ADDRESS.equals(clientIp)
                || LOCALHOST_NAME.equals(clientIp)
                || LOCALHOST_IPV4_ADDRESS.equals(clientIp)){
            clientIp = "127.0.0.1";
        }
        String class_method = joinPoint.getSignature().getDeclaringTypeName() + "." + joinPoint.getSignature().getName();
        String param = Arrays.toString(joinPoint.getArgs());

        MethodSignature sign =  (MethodSignature)joinPoint.getSignature();
        Method method2 = sign.getMethod();
        AuditLog annotation = method2.getAnnotation(AuditLog.class);

        AuditServiceLog auditServiceLog =  new AuditServiceLog();
        auditServiceLog.setUrl(url);
        auditServiceLog.setMethod(method);
        auditServiceLog.setRemoteAddr(remoteAddr);
        auditServiceLog.setClientIp(clientIp);
        auditServiceLog.setClassMethod(class_method);
        if (log.isDebugEnabled()) {
            auditServiceLog.setParam(param);
        }
        auditServiceLog.setAnnotation(annotation.value());
        auditServiceLog.setQueryKey(DigestUtils.md5Hex(StringUtil.isNotBlank(request.getQueryString()) ? request.getQueryString() : ""));

        auditLog.set(auditServiceLog);
    }

    @AfterThrowing(value = "annotationPointCut()", throwing = "exception")
    public void doAfterThrowingAdvice(JoinPoint joinPoint,Throwable exception){
        log.error("Audit log [error]:" + joinPoint.getSignature().getName() + exception);

    }


    @After(value = "annotationPointCut()")
    public void after(JoinPoint joinPoint) {
        ApiParam param = (ApiParam) joinPoint.getArgs()[0];
        auditLog.get().setDbType(param.getDbType());

    }




    @AfterReturning(returning = "result", pointcut = "annotationPointCut()")
    public void doAfterReturning(Object result) throws Throwable {

        AuditServiceLog logAfter =  auditLog.get();
        if (StringUtil.isNotEmpty(result) ) {
            QueryCacheUtils.put(logAfter.getQueryKey(), result);
        }

        if(result instanceof BaseResult){
            BaseResult baseResult = (BaseResult) result;
            baseResult.setQueryKey(logAfter.getQueryKey());
        }

        Long exeTime = System.currentTimeMillis() - startTime.get();
        logAfter.setExeTime(exeTime);

        log.info("Audit Log [completed]：" + JsonMapper.toJsonString(logAfter));

    }



}
