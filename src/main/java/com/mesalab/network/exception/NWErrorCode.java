package com.mesalab.network.exception;

import lombok.Getter;

/**
 * @Author wxs
 * @Date 2022/11/3
 */
@Getter
public enum NWErrorCode {
    /**
     * 1-3位：异常类型（HTTP协议状态码）
     * 4-5位：模块 04
     * 6-8：自然排序
     */


    // 请求参数校验错误
    PARAMETER_ERROR("40004100", "Parameter Validation Failed:%s"),
    // 未知异常
    UNKNOWN_EXCEPTION("55504999", "Unknown exception: %s"),



    ;


    private String code;
    private String message;

    NWErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

}