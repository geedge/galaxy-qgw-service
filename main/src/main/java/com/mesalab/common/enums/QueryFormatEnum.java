package com.mesalab.common.enums;

import lombok.Getter;

@Getter
public enum QueryFormatEnum {
    JSON("json"),
    CSV("csv"),;
    private String value;
    QueryFormatEnum(String value) {this.value = value;}
}
