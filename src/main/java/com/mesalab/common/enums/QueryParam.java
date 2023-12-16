package com.mesalab.common.enums;

import lombok.Getter;

@Getter
public enum QueryParam {
    QUERY("query"),
    FORMAT("format"),
    OPTION("option"),;
    private String value;
    QueryParam(String value) {this.value = value;}
}
