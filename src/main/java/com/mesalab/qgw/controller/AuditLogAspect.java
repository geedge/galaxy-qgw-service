package com.mesalab.qgw.controller;

import com.mesalab.common.utils.IPUtil;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.qgw.model.api.AuditLog;
import com.mesalab.qgw.model.api.AuditServiceLog;
import com.zdjizhi.utils.CommonUtil;
import com.zdjizhi.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
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
    AuditServiceLog auditServiceLog =  new AuditServiceLog();
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

        auditServiceLog.setParam(url);
        auditServiceLog.setMethod(method);
        auditServiceLog.setRemoteAddr(remoteAddr);
        auditServiceLog.setClientIp(clientIp);
        auditServiceLog.setClassMethod(class_method);
        auditServiceLog.setParam(param);
        auditServiceLog.setAnnotation(annotation.value());

        log.set(auditServiceLog);
    }

    @AfterThrowing(value = "annotationPointCut()", throwing = "exception")
    public void doAfterThrowingAdvice(JoinPoint joinPoint,Throwable exception){

        logger.error("Audit log [error]:" + joinPoint.getSignature().getName() + exception);

    }



    @AfterReturning(returning = "ret", pointcut = "annotationPointCut()")
    public void doAfterReturning(Object ret) throws Throwable {
        String result = ret.toString();
        AuditServiceLog logAfter =  log.get();
        Long exeTime = System.currentTimeMillis() - startTime.get();
        logAfter.setExeTime(exeTime);

        logger.debug("Audit Log [completed]：" + JsonMapper.toJsonString(logAfter));

    }



}
