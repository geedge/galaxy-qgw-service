package com.mesalab.qgw.controller;

import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {


    @ExceptionHandler()
    public BaseResult handleException(Exception e) {
        return BaseResultGenerator.failure(e.getMessage()); //自己需要实现的异常处理
    }




    @ExceptionHandler({RuntimeException.class})
    public BaseResult handleBusinessException(BusinessException e, HttpServletRequest request, HttpServletResponse response) {
       response.setStatus(e.getErrorCode());
       log.error("Execute Query Error: {}", e.getMessage()+ ";" + (e.getCause() != null ? e.getCause().getMessage() : ""));
       return BaseResultGenerator.failure(e.getErrorCode(), e.getMessage()+ ";" + (e.getCause() != null ? e.getCause().getMessage() : ""));//自己需要实现的异常处理
    }


 }
