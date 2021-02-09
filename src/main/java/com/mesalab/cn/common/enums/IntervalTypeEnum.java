package com.mesalab.cn.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Date: 2020-07-29 15:45
 * @Author : wangwei
 * @ClassName : IntervalTypeEnum
 * @Description : 区间查询类型枚举
 */
@Getter
@AllArgsConstructor
public enum IntervalTypeEnum {
    EXACTLY("BETWEEN", "闭区间间隔");

    private String type;
    private String name;

    public static IntervalTypeEnum getByType(String type) {
        for (IntervalTypeEnum constants : values()) {
            if (constants.type.equalsIgnoreCase(type)) {
                return constants;
            }
        }
        return null;
    }
}
