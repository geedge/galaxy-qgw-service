package com.measlab.tsg.exception;

import lombok.Getter;

/**
 * @Author wxs
 * @Date 2022/11/3
 */
@Getter
public enum TSGErrorCode {
    /**
     * 1-3位：异常类型（HTTP协议状态码）
     * 4-5位：模块 04
     * 6-8：自然排序
     */

    SQL_BUILDER_EXCEPTION("50004100", "Error in SQL query builder and optimizer: %s"),



    ;
    private String code;
    private String message;

    TSGErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
