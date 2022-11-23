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
     * 3-4位：模块 02
     * 5-7：自然排序
     */

    // 请求参数校验错误
    PARAMETER_ERROR("40002100", "Parameter Validation Failed:%s"),
    // SQL解析错误
    SQL_SYNTAX_PARSE_EXCEPTION("40002300", "Syntax Error in SQL statement: %s"),
    SQL_EXECUTION_BAD_REQUEST_EXCEPTION("40002301", "Error in Database Execution Engine: %s"),
    // SQL构建错误
    SQL_BUILDER_EXCEPTION("50002100", "Error in SQL Query Builder and Optimizer: %s"),
    // SQL在数据库查询中执行错误
    SQL_EXECUTION_SERVER_EXCEPTION("50002300", "Error in Database Execution Engine: %s"),
    // SQL在联邦查询中执行错误
    ENGINE_EXECUTION_EXCEPTION("50002500", "Error in Query Federation: %s"),
    // 未知异常
    UNKNOWN_EXCEPTION("55502999", "Unknown exception: %s"),




    ;
    private String code;
    private String message;

    KnowLedgeErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
