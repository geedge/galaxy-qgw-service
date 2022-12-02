package com.mesalab.qgw.exception;

import lombok.Getter;

/**
 * @Author wxs
 * @Date 2022/11/3
 */
@Getter
public enum QGWErrorCode {

    /**
     * 1-3位：异常类型（HTTP协议状态码）
     * 3-4位：模块 01
     * 5-7：自然排序
     */
    // SQL解析错误
    SQL_SYNTAX_PARSE_EXCEPTION("40001300", "Syntax Error in SQL statement: %s"),
    // 数据库引擎错误
    SQL_EXECUTION_BAD_REQUEST_EXCEPTION("40001301", "Error in Database Execution Engine: %s"),
    // SQL构建错误
    SQL_BUILDER_EXCEPTION("50001100", "Error in SQL Query Builder and Optimizer: %s"),
    // SQL在数据库查询中执行错误
    SQL_EXECUTION_SERVER_EXCEPTION("50001300", "Error in Database Execution Engine: %s"),
    // SQL在联邦查询中执行错误
    SQL_QUERY_FEDERATION_EXCEPTION("50001500", "Error in Query Federation: %s"),
    ;
    private String code;
    private String message;

    QGWErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
