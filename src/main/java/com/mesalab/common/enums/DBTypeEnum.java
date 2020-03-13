package com.mesalab.common.enums;

import lombok.Getter;

@Getter
public enum DBTypeEnum {
    CLICKHOUSE("clickhouse"),
    DRUID("druid"),
    HBASE("hbase"),
    INFLUXDB("influxdb"),;
    private String value;
    DBTypeEnum(String value) {this.value = value;}
}
