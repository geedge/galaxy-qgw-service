package com.mesalab.qgw.controller;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.collect.Maps;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.JsonMapper;
import com.zdjizhi.utils.StringUtil;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Log log = LogFactory.get();

    @ExceptionHandler(Exception.class)
    public BaseResult handleException(Exception e, HttpServletRequest request, HttpServletResponse response) {
        response.setStatus(ResultStatusEnum.FAIL.getCode());
        String queryKey = DigestUtil.md5Hex(StringUtil.createUUID());
        String message = e.getMessage() + (e.getCause() != null ? e.getCause().getMessage() : "");
        log.error("queryKey:{}, message:{}, stackTrace:{}", queryKey, message, getStackTrace(e));
        return BaseResultGenerator.failure(e.getMessage(), queryKey);
    }




    @ExceptionHandler({BusinessException.class})
    public BaseResult handleBusinessException(BusinessException e, HttpServletRequest request, HttpServletResponse response) {
        response.setStatus(e.getErrorStatus());
        String queryKey = DigestUtil.md5Hex(StringUtil.createUUID());
        String message = (e.getMessage() != null ? e.getMessage() : e.getErrorMessage()) + "  " + (e.getCause() != null ? e.getCause().getMessage() : "");
        log.error("queryKey:{}, message:{}, stackTrace:{}", queryKey, message, getStackTrace(e));
       return BaseResultGenerator.failure(e.getErrorStatus(), e.getErrorCode(), queryKey, message);
    }

    private String getStackTrace(Exception e) {
        return StringUtil.isNotEmpty(e.getStackTrace()) ? e.getStackTrace()[0].toString() : "";
    }



 }
