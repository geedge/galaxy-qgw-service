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
    ILLEGAL_ARGUMENT_EXCEPTION("400001", "Illegal argument exception"),
    SQL_SYNTAX_PARSE_EXCEPTION("400010", "SQL syntax parse exception"),
    SQL_EXECUTION_EXCEPTION("500001", "SQL execution exception"),
    ENGINE_EXECUTION_EXCEPTION("500010", "Engine execution exception"),

    ENGINE_SYNTAX_PARSE_EXCEPTION("500100", "Engine syntax parse exception"),
    UNKNOW_EXCEPTION("555999", "Unknown exception");

    private String code;
    private String message;

    ResultCodeEnum(String code, String message) {
        this.code = code;
        this.message = message;
    }

}
