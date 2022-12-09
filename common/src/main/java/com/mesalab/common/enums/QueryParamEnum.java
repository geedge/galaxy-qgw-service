package com.mesalab.common.enums;

import lombok.Getter;

@Getter
public enum QueryParamEnum {
    QUERY("query"),
    FORMAT("format"),
    OPTION("option"),;
    private String value;
    QueryParamEnum(String value) {this.value = value;}
}
