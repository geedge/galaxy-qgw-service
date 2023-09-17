package com.mesalab.common.enums;

import lombok.Getter;

@Getter
public enum DiagnosisCategoryEnum {
    DEFAULT_DASHBOARD("default-dashboard"),
    TROUBLE_SHOOTING("trouble-shooting"),
    BENCHMARK("benchmark");

    private String value;

    DiagnosisCategoryEnum(String value) {
        this.value = value;
    }

}
