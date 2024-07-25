package com.mesalab.qgw.aspect;

import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson2.JSON;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.utils.MDCUtil;
import com.mesalab.qgw.model.basic.EngineConfigSource;
import com.mesalab.qgw.model.basic.SQLQueryContext;
import com.mesalab.qgw.model.basic.AuditLog;
import com.mesalab.qgw.model.basic.AuditServiceLog;
import com.geedgenetworks.utils.IPUtil;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Arrays;

@Aspect
@Component
public class AuditLogAspect {

    private static final Log log = LogFactory.get();
    private ThreadLocal<Long> startTime = ThreadUtil.createThreadLocal(false);
    private ThreadLocal<AuditServiceLog> auditLog = ThreadUtil.createThreadLocal(false);
    private EngineConfigSource engineConfigSource;

    @Pointcut("@annotation(com.mesalab.qgw.model.basic.AuditLog)")
    public void annotationPointCut() {
    }

    @Before("annotationPointCut()")
    public void doBefore(JoinPoint joinPoint) {
        startTime.set(System.currentTimeMillis());
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        AuditServiceLog auditServiceLog = new AuditServiceLog();
        String url = null;

        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            url = request.getRequestURL().toString();
            String method = request.getMethod();
            String remoteAddr = request.getRemoteAddr();
            String clientIp = IPUtil.getIpAddr(request);

            auditServiceLog.setRemoteAddr(remoteAddr);
            auditServiceLog.setClientIp(clientIp);
            auditServiceLog.setMethod(method);
        }

        String classMethod = joinPoint.getSignature().getDeclaringTypeName() + "." + joinPoint.getSignature().getName();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        AuditLog annotation = method.getAnnotation(AuditLog.class);

        auditServiceLog.setRequestParam(Arrays.toString(joinPoint.getArgs()));
        auditServiceLog.setUrl(url);
        auditServiceLog.setClassMethod(classMethod);
        auditServiceLog.setAnnotation(annotation.value());
        auditServiceLog.setRequestId(MDCUtil.getTraceId());

        auditLog.set(auditServiceLog);
    }

    @AfterThrowing(value = "annotationPointCut()", throwing = "exception")
    public void doAfterThrowingAdvice(JoinPoint joinPoint, Throwable exception) {
        AuditServiceLog logAfter = auditLog.get();
        logAfter.setRequestParam(Arrays.toString(joinPoint.getArgs()));
        log.error("Audit Log [error]: {}, reason {}", JSON.toJSONString(logAfter), exception.getMessage());
    }


    @After("annotationPointCut()")
    public void after(JoinPoint joinPoint) {
        AuditServiceLog logAfter = auditLog.get();
        Object[] args = joinPoint.getArgs();

        if (args != null && args.length > 0 && args[0] instanceof SQLQueryContext) {
            SQLQueryContext queryContext = (SQLQueryContext) args[0];
            logAfter.setDbEngine(queryContext.getDbEngine());
        }

        long elapsed = System.currentTimeMillis() - startTime.get();
        logAfter.setElapsed(elapsed);

        String logMessage = StrUtil.format("Audit Log [done]: {}", JSON.toJSONString(logAfter));
        log.info(logMessage);

        if (elapsed > engineConfigSource.getHighLatencyThreshold()) {
            log.warn(logMessage);
        }
        // Clear ThreadLocal variables to prevent potential memory leaks
        clearThreadLocal();
    }

    @AfterReturning(returning = "result", pointcut = "annotationPointCut()")
    public void doAfterReturning(Object result) {
        AuditServiceLog logAfter = auditLog.get();
        if (result instanceof BaseResult) {
            BaseResult baseResult = (BaseResult) result;
            baseResult.setRequestId(logAfter.getRequestId());
        }
        log.debug("Audit Log [success]: result {}", result);
    }

    private void clearThreadLocal() {
        if (startTime != null) {
            startTime.remove();
        }
        if (auditLog != null) {
            auditLog.remove();
        }
    }

    @Autowired
    private void setEngineConfigSource(EngineConfigSource engineConfigSource) {
        this.engineConfigSource = engineConfigSource;
    }
}
