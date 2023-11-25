package com.mesalab.common.enums;

import lombok.Getter;

@Getter
public enum QueryOptionEnum {
    REAL_TIME("real_time"),
    LONG_TERM("long_term"),
    SYNTAX_VALIDATION("syntax_validation"),
    SYNTAX_PARSE("syntax_parse");
    private String value;
    QueryOptionEnum(String value) {this.value = value;}

}
