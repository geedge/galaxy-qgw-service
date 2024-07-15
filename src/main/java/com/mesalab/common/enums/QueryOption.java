package com.mesalab.common.enums;

import lombok.Getter;

@Getter
public enum QueryOption {
    REAL_TIME("real_time"),
    LONG_TERM("long_term"),
    SYNTAX_VALIDATION("syntax_validation"),
    SYNTAX_PARSE("syntax_parse");
    private String value;
    QueryOption(String value) {this.value = value;}

}
