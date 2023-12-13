package com.mesalab.common.enums;

import lombok.Getter;

@Getter
public enum OutputModeEnum {
    JSON("json"),
    CSV("csv"),;
    private final String value;
    OutputModeEnum(String value) {this.value = value;}
}
