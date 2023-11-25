package com.mesalab.services.common.enums;

import com.mesalab.cn.enums.EngineTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * TODO
 *
 * @Classname MetricsType
 * @Date 2023/4/19 10:46
 * @Author wWei
 */
@Getter
@AllArgsConstructor
public enum MetricType {

    SESSIONS("sessions"),
    BYTES("bytes"),
    INCOMING_BYTES("incoming_bytes"),
    OUTGOING_BYTES("outgoing_bytes");
    private final String type;

    public static boolean isValid(String metric) {
        for (MetricType item : values()) {
            if (item.type.equals(metric)) {
                return true;
            }
        }
        return false;
    }
}
