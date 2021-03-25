package com.mesalab.qgw.controller;

import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.utils.QueryCacheUtils;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.AuditLog;
import com.mesalab.qgw.model.api.AuditServiceLog;
import com.zdjizhi.utils.IPUtil;
import com.zdjizhi.utils.StringUtil;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Arrays;


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
        String queryString = request.getQueryString();
        auditServiceLog.setQueryKey(DigestUtil.md5Hex(StringUtil.isNotBlank(queryString) ? queryString : ""));

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
    public void doAfterReturning(Object result) {

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
