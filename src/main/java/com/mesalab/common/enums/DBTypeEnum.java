package com.mesalab.common.enums;

import lombok.Getter;

@Getter
public enum DBTypeEnum {
    CLICKHOUSE("clickHouse"),
    DRUID("druid"),
    HBASE("hbase"),;
    private String value;
    DBTypeEnum(String value) {this.value = value;}
}
