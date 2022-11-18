package com.mesalab.qgw.exception;

import lombok.Getter;

/**
 * @Author wxs
 * @Date 2022/11/3
 */
@Getter
public enum QGWErrorCode {

    //",

    /**
     * 1-2位：异常类型（参数检查异常:01、SQL语句检查异常:02、SQL执行异常:03、引擎计算异常:04、解析异常:05、执行失败:99）。
     * 3-4位：模块 04
     * 5-7：自然排序
     */

    SQL_REWRITE_ERROR("0204001", "SQL Rewrite add tableName and Alias Error:%s"),

    // 请求参数校验错误
    PARAMETER_ERROR("40001100", "Parameter Validation Failed:%s"),
    // SQL解析错误
    SQL_SYNTAX_PARSE_EXCEPTION("40001300", "Syntax Error in SQL statement: %s"),
    // SQL构建错误
    SQL_BUILDER_EXCEPTION("50001100", "Error in SQL Query Builder and Optimizer: [*]"),
    // SQL在数据库查询中执行错误
    SQL_EXECUTION_EXCEPTION("50001300", "Error in Database Execution Engine:%s"),
    // SQL在联邦查询中执行错误
    ENGINE_EXECUTION_EXCEPTION("50001500", "Error in Query Federation:%s"),
    // 未知异常
    UNKNOWN_EXCEPTION("55501999", "Unknown exception:%s");


    ;
    private String code;
    private String message;

    QGWErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
