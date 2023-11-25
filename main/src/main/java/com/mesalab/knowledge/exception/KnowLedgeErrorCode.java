package com.mesalab.knowledge.exception;

import lombok.Getter;

/**
 * @Author wxs
 * @Date 2022/11/3
 */
@Getter
public enum KnowLedgeErrorCode {
    /**
     * 1-3位：异常类型（HTTP协议状态码）
     * 4-5位：模块 03
     * 6-8：自然排序
     */

    ;
    private String code;
    private String message;

    KnowLedgeErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
