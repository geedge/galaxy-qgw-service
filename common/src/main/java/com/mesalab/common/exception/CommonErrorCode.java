package com.mesalab.common.exception;

import lombok.Getter;

/**
 * @Author wxs
 * @Date 2022/11/3
 */
@Getter
public enum CommonErrorCode {

    /**
     * 1-3位：异常类型（HTTP协议状态码）
     * 3-4位：模块 01
     * 5-7：自然排序
     */
    PARAMETER_ERROR("40000100", "Bad request - Invalid query parameters :%s"),
    BAD_REQUEST_SQL_SYNTAX_PARSE_EXCEPTION("40001300", "Error in SQL syntax parsing: %s"),
    BAD_REQUEST_SQL_EXECUTION_EXCEPTION("40001301", "Error in database execution engine: %s"),
    BAD_REQUEST_PARAM_SYNTAX_EXCEPTION("40001302", " Invalid Request Parameter: %s"),

    SCHEMA_AND_TABLE_NOT_CONSISTENCY("50001010", "Schema and table structure are not consistent: %s"),
    HTTP_REQUEST_EXCEPTION("50001011", "Error in HTTP request : %s"),
    SQL_REWRITE_AND_TRANSFORMATION_EXCEPTION("50001100", "Error in SQL rewrite and transformation: %s"),
    SQL_EXECUTION_EXCEPTION("50001300", "Error in SQL execution: %s"),
    SQL_FEDERATION_QUERY_EXCEPTION("50001500", "Error in SQL federation query: %s"),

    UNKNOWN_EXCEPTION("50000999", "An unknown error occurred: %s");

    private String code;
    private String message;

    CommonErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
