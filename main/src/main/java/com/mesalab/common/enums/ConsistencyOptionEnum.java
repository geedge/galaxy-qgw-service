package com.mesalab.common.enums;

import lombok.Getter;

@Getter
public enum ConsistencyOptionEnum {

    TTL_CONSISTENCY("ttl_consistency"),
    SCHEMA_CONSISTENCY("schema_consistency");
    private String value;
    ConsistencyOptionEnum(String value){this.value = value;}
}
