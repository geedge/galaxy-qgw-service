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

    EXECUTE_SUCCESS("200666", "Success"),
    PARAM_SYNTAX_ERROR("400001", "Parameter check exception"),
    SQL_SYNTAX_ERROR("400010", "SQL statement check exception"),
    SQL_EXECUTION_ERROR("500001", "SQL execution exception"),
    ENGINE_STATISTICS_ERROR("500010", "Engine calculation exception"),
    ENGINE_PARSE_ERROR("500100", "Parse exception"),
    UNKNOW_ERROR("555999", "Failed to execute");



    private  String code;
    private String message;

    ResultCodeEnum(String code, String message) {
        this.code = code;
    }

}
