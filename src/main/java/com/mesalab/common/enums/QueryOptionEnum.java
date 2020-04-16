package com.mesalab.common.enums;

import lombok.Getter;

@Getter
public enum QueryOptionEnum {
    REAL_TIME("real-time"),
    LONG_TERM("long-term"),
    SYNTAX_CHECK("syntax-check"),

    QUERY_SCHEMA("query-schema"),;//用于schema查询接口做标记
    private String value;
    QueryOptionEnum(String value) {this.value = value;}


}
