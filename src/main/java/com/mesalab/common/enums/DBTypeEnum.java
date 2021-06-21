package com.mesalab.common.enums;

import lombok.Getter;

@Getter
public enum DBTypeEnum {
    ENGINE("engine"),
    CLICKHOUSE("clickHouse"),
    DRUID("druid"),
    HBASE("hbase"),
    ELASTICSEARCH("elasticsearch"),;
    private String value;
    DBTypeEnum(String value) {this.value = value;}
}
