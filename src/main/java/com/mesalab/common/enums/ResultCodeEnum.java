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
    SUCCESS("20000666", "Success");

    private String code;
    private String message;

    ResultCodeEnum(String code, String message) {
        this.code = code;
        this.message = message;
    }

}
