package com.mesalab.qgw.interceptor;

import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.HttpStatusCodeEnum;
import com.mesalab.common.exception.BusinessException;;
import com.mesalab.common.utils.MDCUtil;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.geedgenetworks.utils.StringUtil;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Log log = LogFactory.get();


    /**
     * 捕获特定异常
     *
     * @param e
     * @param request
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void handleAsyncRequestTimeoutException(AsyncRequestTimeoutException e, HttpServletRequest request) {
        log.info("Handle Async Request Timeout Exception");
    }


    @ExceptionHandler(Exception.class)
    public BaseResult handleException(Exception e, HttpServletRequest request, HttpServletResponse response) {
        response.setStatus(HttpStatusCodeEnum.BAD_REQUEST.getCode());
        String queryKey = StrUtil.isEmpty(MDCUtil.getTraceId()) ? DigestUtil.md5Hex(StringUtil.createUUID()) : MDCUtil.getTraceId();
        log.error("requestId:{}, message:{}, stackTrace:{}", queryKey, ExceptionUtil.getRootCauseMessage(e), getStackTrace(e));
        return BaseResultGenerator.failure(e.getMessage(), queryKey);
    }


    @ExceptionHandler({QGWBusinessException.class})
    public BaseResult handleBusinessException(QGWBusinessException e, HttpServletRequest request, HttpServletResponse response) {
        response.setStatus(e.getStatus());
        String queryKey = StrUtil.isEmpty(MDCUtil.getTraceId()) ? DigestUtil.md5Hex(StringUtil.createUUID()) : MDCUtil.getTraceId();
        String message = (null != e.getMessage()) ? e.getMessage() : ExceptionUtil.getRootCauseMessage(e);
        log.error("requestId:{}.message:{}.stackTrace:{}", queryKey, message, getStackTrace(e));
        return BaseResultGenerator.failure(e.getStatus(), e.getCode(), queryKey, message);
    }

    @ExceptionHandler({BusinessException.class})
    public BaseResult handleBusinessException(BusinessException e, HttpServletRequest request, HttpServletResponse response) {
        response.setStatus(e.getStatus());
        String queryKey = StrUtil.isEmpty(MDCUtil.getTraceId()) ? DigestUtil.md5Hex(StringUtil.createUUID()) : MDCUtil.getTraceId();
        String message = (null != e.getMessage()) ? e.getMessage() : ExceptionUtil.getRootCauseMessage(e);
        log.error("requestId:{}.message:{}.stackTrace:{}", queryKey, message, getStackTrace(e));
        return BaseResultGenerator.failure(e.getStatus(), e.getCode(), queryKey, message);
    }

    private String getStackTrace(Exception e) {
        return StringUtil.isNotEmpty(e.getStackTrace()) ? e.getStackTrace()[0].toString() : "";
    }

}
