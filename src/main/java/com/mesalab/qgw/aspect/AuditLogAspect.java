package com.mesalab.qgw.aspect;

import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson2.JSON;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.utils.HttpHelper;
import com.mesalab.common.utils.MDCUtil;
import com.mesalab.qgw.model.basic.SQLQueryContext;
import com.mesalab.qgw.model.basic.AuditLog;
import com.mesalab.qgw.model.basic.AuditServiceLog;
import com.geedgenetworks.utils.IPUtil;
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


    @Pointcut("@annotation(com.mesalab.qgw.model.basic.AuditLog)")
    public void annotationPointCut() {
    }

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

        MethodSignature sign = (MethodSignature) joinPoint.getSignature();
        Method method2 = sign.getMethod();
        AuditLog annotation = method2.getAnnotation(AuditLog.class);
        AuditServiceLog auditServiceLog = new AuditServiceLog();
        auditServiceLog.setAnnotation(annotation.value());
        auditServiceLog.setRemoteAddr(remoteAddr);
        auditServiceLog.setClientIp(clientIp);
        auditServiceLog.setMethod(method);
        if (log.isInfoEnabled()) {
            auditServiceLog.setParam(Arrays.toString(joinPoint.getArgs()));
            auditServiceLog.setUrl(url);
            auditServiceLog.setClassMethod(classMethod);
        }
        auditServiceLog.setCacheKey(HttpHelper.generateCacheKey(request));
        auditServiceLog.setRequestId(MDCUtil.getTraceId());
        auditLog.set(auditServiceLog);
    }

    @AfterThrowing(value = "annotationPointCut()", throwing = "exception")
    public void doAfterThrowingAdvice(JoinPoint joinPoint, Throwable exception) {
        AuditServiceLog logAfter = auditLog.get();
        logAfter.setParam(Arrays.toString(joinPoint.getArgs()));
        log.error("Audit Log [error]:{}", JSON.toJSONString(logAfter));
    }


    @After(value = "annotationPointCut()")
    public void after(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args.length == 0) {
            return;
        }
        if (args[0] instanceof SQLQueryContext) {
            SQLQueryContext queryContext = (SQLQueryContext) joinPoint.getArgs()[0];
            auditLog.get().setDbEngine(queryContext.getDbEngine());
        }
    }

    @AfterReturning(returning = "result", pointcut = "annotationPointCut()")
    public void doAfterReturning(Object result) {
        AuditServiceLog logAfter = auditLog.get();
        if (result instanceof BaseResult) {
            BaseResult baseResult = (BaseResult) result;
            baseResult.setRequestId(logAfter.getRequestId());
        }
        Long exeTime = System.currentTimeMillis() - startTime.get();
        logAfter.setExeTime(exeTime);
        log.info("Audit Log [success]: {}", JSON.toJSONString(logAfter));
    }


}
