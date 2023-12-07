package com.mesalab.common.enums;

import lombok.Getter;

@Getter
public enum OutputModeEnum {
    JSON("json"),
    CSV("csv"),;
    private String value;
    OutputModeEnum(String value) {this.value = value;}
}
