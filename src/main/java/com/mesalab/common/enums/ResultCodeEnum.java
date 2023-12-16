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


    SUCCESS("20000666", "Success"),

    PARAMETER_ERROR("40000100", "Bad request - Invalid query parameters :%s"),

    UNKNOWN_EXCEPTION("50000999", "An unknown error occurred: %s");

    private String code;
    private String message;

    ResultCodeEnum(String code, String message) {
        this.code = code;
        this.message = message;
    }

}
