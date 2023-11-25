package com.mesalab.cn.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Date: 2020-07-22 17:45
 * @Author : liuyongqiang
 * @ClassName : MatchTypeEnum
 * @Description : 匹配查询类型枚举
 */
@Getter
@AllArgsConstructor
public enum MatchTypeEnum {

    EXACTLY("exactly", "完全匹配"),
    PREFIX("prefix", "前缀匹配"),
    SUFFIX("suffix", "后缀匹配"),
    SUBSTRING("substring", "字串匹配"),
    REGEX("regex", "正则匹配");

    private String type;
    private String name;

    public static MatchTypeEnum getByType(String type) {
        for (MatchTypeEnum constants : values()) {
            if (constants.type.equalsIgnoreCase(type)) {
                return constants;
            }
        }
        return null;
    }
}
