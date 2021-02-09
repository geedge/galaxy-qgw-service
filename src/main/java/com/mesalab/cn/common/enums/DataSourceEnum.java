package com.mesalab.cn.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author wangwei
 * @description:
 * @date 2020/11/12 5:39 下午
 */
@Getter
@AllArgsConstructor
public enum DataSourceEnum {
    TUNNEL("tunnel", "闭区间间隔");

    private String type;
    private String name;

    public static DataSourceEnum getByType(String type) {
        for (DataSourceEnum constants : values()) {
            if (constants.type.equalsIgnoreCase(type)) {
                return constants;
            }
        }
        return null;
    }
}
