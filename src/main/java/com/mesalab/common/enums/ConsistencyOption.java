package com.mesalab.common.enums;

import lombok.Getter;

@Getter
public enum ConsistencyOption {

    TTL_CONSISTENCY("ttl_consistency"),
    SCHEMA_CONSISTENCY("schema_consistency"),
    DATASET_VERIFICATION("dataset_verification");
    private String value;
    ConsistencyOption(String value){this.value = value;}
}
