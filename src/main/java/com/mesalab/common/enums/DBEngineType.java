package com.mesalab.common.enums;

import lombok.Getter;

@Getter
public enum DBEngineType {
    QGW("qgw"),
    CLICKHOUSE("clickHouse"),
    DRUID("druid"),
    STAR_ROCKS("starRocks"),
    HBASE("hbase"),
    ARANGODB("arangoDB");
    private String value;
    DBEngineType(String value) {this.value = value;}
}
