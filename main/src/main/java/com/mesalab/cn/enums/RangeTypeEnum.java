package com.mesalab.cn.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Date: 2020-07-22 17:44
 * @Author : liuyongqiang
 * @ClassName : RangeTypeEnum
 * @Description : 范围查询类型枚举
 */
@Getter
@AllArgsConstructor
public enum RangeTypeEnum {

    GT("gt", " > "),
    LT("lt", " < "),
    EQ("eq", " = "),
    GE("ge", " >= "),
    LE("le", " <= "),
    NE("ne", " != ");

    private String type;
    private String expr;

    public static RangeTypeEnum getByType(String type) {
        for (RangeTypeEnum constants : values()) {
            if (constants.type.equalsIgnoreCase(type)) {
                return constants;
            }
        }
        return null;
    }
}
