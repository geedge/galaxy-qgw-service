package com.mesalab.common.enums;

import lombok.Getter;

@Getter
public enum OutputMode {
    JSON("json"),
    CSV("csv"),;
    private final String value;
    OutputMode(String value) {this.value = value;}
}
