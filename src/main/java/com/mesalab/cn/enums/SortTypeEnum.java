package com.mesalab.cn.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Date: 2020-11-22 17:45
 * @Author : wWei
 * @ClassName : LogicTypeEnum
 * @Description : 逻辑关系枚举
 */
@Getter
@AllArgsConstructor
public enum SortTypeEnum {

    DESC("desc"),
    ASC("asc");
    private String type;

    public static SortTypeEnum getByType(String type) {
        for (SortTypeEnum constants : values()) {
            if (constants.type.equalsIgnoreCase(type)) {
                return constants;
            }
        }
        return null;
    }
}
