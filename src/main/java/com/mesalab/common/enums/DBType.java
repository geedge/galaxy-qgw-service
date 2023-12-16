package com.mesalab.common.enums;

import lombok.Getter;

@Getter
public enum DBType {
    ENGINE("qgw"),
    CLICKHOUSE("clickHouse"),
    DRUID("druid"),
    HBASE("hbase"),
    ARANGODB("arangoDB");
    private String value;
    DBType(String value) {this.value = value;}
}
