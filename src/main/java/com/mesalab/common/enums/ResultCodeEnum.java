package com.mesalab.common.enums;

import lombok.Getter;

/**
 * BaseResult的业务状态码
 *
 * @author
 * @date
 */
@Getter
public enum ResultCodeEnum {


    SUCCESS("200666", "Success"),
    // 请求参数校验错误
    PARAMETER_ERROR("40000100", "Parameter Validation Failed:%s"),
    // 未知异常
    UNKNOWN_EXCEPTION("50000999", "Unknown exception: %s");

    private String code;
    private String message;

    ResultCodeEnum(String code, String message) {
        this.code = code;
        this.message = message;
    }

}
