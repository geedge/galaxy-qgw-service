package com.mesalab.common.enums;

import lombok.Getter;

/**
 * TODO
 *
 * @Classname StorageQuotaLogType
 * @Date 2024/8/30 09:54
 * @Author wWei
 */
@Getter
public enum StorageQuotaLogType {
    TRAFFIC_LOGS("Traffic Logs"),
    METRICS("Metrics"),
    FILES("Files"),
    ALL("All");

    private String value;

    StorageQuotaLogType(String value) {
        this.value = value;
    }
}