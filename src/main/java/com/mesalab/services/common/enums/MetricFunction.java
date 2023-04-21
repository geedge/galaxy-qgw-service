package com.mesalab.services.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * TODO
 *
 * @Classname MetricFuntion
 * @Date 2023/4/19 11:13
 * @Author wWei
 */
@Getter
@AllArgsConstructor
public enum MetricFunction {
    SUM("sum");

    private final String type;

    public static boolean isValid(String function) {
        for (MetricFunction item : values()) {
            if (item.type.equals(function)) {
                return true;
            }
        }
        return false;
    }
}
