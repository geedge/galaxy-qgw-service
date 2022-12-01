package com.mesalab.cn.exception;

import lombok.Getter;

/**
 * @Author wxs
 * @Date 2022/11/3
 */
@Getter
public enum CNErrorCode {

    /**
     * 1-3位：异常类型（HTTP协议状态码）
     * 4-5位：模块 02
     * 6-8：自然排序
     */

    // 请求参数校验错误
    PARAMETER_ERROR("40002100", "Parameter Validation Failed:%s"),
    // SQL构建错误
    SQL_BUILDER_EXCEPTION("50002100", "Error in SQL Query Builder and Optimizer: %s"),
    // 未知异常
    UNKNOWN_EXCEPTION("55502999", "Unknown exception: %s"),



    ;


    private String code;
    private String message;

    CNErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
