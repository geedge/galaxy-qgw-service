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

    EXECUTE_SUCCESS("200666", "成功"),
    PARAM_SYNTAX_ERROR("400001", "参数检查异常"),
    SQL_SYNTAX_ERROR("400010", "SQL语句检查异常"),
    SQL_EXECUTION_ERROR("500001", "SQL 执行异常"),
    ENGINE_STATISTICS_ERROR("500010", "引擎计算异常"),
    UNKNOW_ERROR("555999", "执行失败");



    private  String code;
    private String message;

    ResultCodeEnum(String code, String message) {
        this.code = code;
    }

}
