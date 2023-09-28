package com.mesalab.common.enums;

import lombok.Getter;

@Getter
public enum DBTypeEnum {
    ENGINE("qgw"),
    CLICKHOUSE("clickHouse"),
    DRUID("druid"),
    HBASE("hbase"),
    ELASTICSEARCH("elasticsearch"),
    ARANGODB("arangoDB");
    private String value;
    DBTypeEnum(String value) {this.value = value;}
}
