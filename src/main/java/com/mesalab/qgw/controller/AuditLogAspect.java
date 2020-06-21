package com.mesalab.qgw.controller;

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

    ThreadLocal<Long> startTime = new ThreadLocal<>();
    ThreadLocal<AuditServiceLog> log = new ThreadLocal<>();
    private static final Logger logger= LoggerFactory.getLogger(AuditLogAspect.class);


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

        if("0.0.0.0".equals(clientIp) || "0:0:0:0:0:0:0:1".equals(clientIp) || "localhost".equals(clientIp) || "127.0.0.1".equals(clientIp)){
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
        if (logger.isDebugEnabled()) {
            auditServiceLog.setParam(param);
        }
        auditServiceLog.setAnnotation(annotation.value());
        auditServiceLog.setQueryKey(DigestUtils.md5Hex(StringUtil.isNotBlank(request.getQueryString()) ? request.getQueryString() : ""));

        log.set(auditServiceLog);
    }

    @AfterThrowing(value = "annotationPointCut()", throwing = "exception")
    public void doAfterThrowingAdvice(JoinPoint joinPoint,Throwable exception){
        logger.error("Audit log [error]:" + joinPoint.getSignature().getName() + exception);

    }


    @After(value = "annotationPointCut()")
    public void after(JoinPoint joinPoint) {
        ApiParam param = (ApiParam) joinPoint.getArgs()[0];
        log.get().setDbType(param.getDbType());

    }




    @AfterReturning(returning = "result", pointcut = "annotationPointCut()")
    public void doAfterReturning(Object result) throws Throwable {

        AuditServiceLog logAfter =  log.get();
        if (StringUtil.isNotEmpty(result) ) {
            QueryCacheUtils.put(logAfter.getQueryKey(), result);
        }

        Long exeTime = System.currentTimeMillis() - startTime.get();
        logAfter.setExeTime(exeTime);

        logger.info("Audit Log [completed]：" + JsonMapper.toJsonString(logAfter));

    }



}
