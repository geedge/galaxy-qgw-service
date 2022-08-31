package com.mesalab.qgw.controller;

import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;;
import com.zdjizhi.utils.StringUtil;
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
     * @param e
     * @param request
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void handleAsyncRequestTimeoutException(AsyncRequestTimeoutException e, HttpServletRequest request) {
        log.info("Handle Async Request Timeout Exception");
    }


    @ExceptionHandler(Exception.class)
    public BaseResult handleException(Exception e, HttpServletRequest request, HttpServletResponse response) {
        response.setStatus(ResultStatusEnum.FAIL.getCode());
        String queryKey = DigestUtil.md5Hex(StringUtil.createUUID());
        log.error("queryKey:{}, message:{}, stackTrace:{}", queryKey, ExceptionUtil.getRootCauseMessage(e), getStackTrace(e));
        return BaseResultGenerator.failure(e.getMessage(), queryKey);
    }




    @ExceptionHandler({BusinessException.class})
    public BaseResult handleBusinessException(BusinessException e, HttpServletRequest request, HttpServletResponse response) {
        response.setStatus(e.getStatus());
        String queryKey = DigestUtil.md5Hex(StringUtil.createUUID());
        String message = (null != e.getMessage()) ? e.getMessage() : ExceptionUtil.getRootCauseMessage(e);
        log.error("queryKey:{}.message:{}.stackTrace:{}", queryKey, message, getStackTrace(e));
       return BaseResultGenerator.failure(e.getStatus(), e.getCode(), queryKey, message);
    }

    private String getStackTrace(Exception e) {
        return StringUtil.isNotEmpty(e.getStackTrace()) ? e.getStackTrace()[0].toString() : "";
    }



 }
