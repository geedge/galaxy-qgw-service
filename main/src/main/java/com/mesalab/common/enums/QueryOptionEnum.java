package com.mesalab.common.enums;

import lombok.Getter;

@Getter
public enum QueryOptionEnum {
    REAL_TIME("real-time"),
    LONG_TERM("long-term"),
    SYNTAX_VALIDATION("syntax-validation"),
    SYNTAX_PARSE("syntax-parse");
    private String value;
    QueryOptionEnum(String value) {this.value = value;}

}
