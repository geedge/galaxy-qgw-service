package com.mesalab.knowledge.enums;

import lombok.AllArgsConstructor;

/**
 * @description: 匹配方式
 * @author: zhq
 * @create: 2020-07-05
 **/
@AllArgsConstructor
public enum MatchEnum {

    EXACTLY("exactly", "\"{0}\""),
    PREFIX("prefix", "\"{0}%\""),
    SUFFIX("suffix", "\"%{0}\""),
    SUBSTRING("substring", "\"%{0}%\"");

    private final String type;
    private final String matchExp;

    public String type() {
        return type;
    }

    public String matchExp() {
        return matchExp;
    }

    public static MatchEnum match(String name, MatchEnum defaultItem) {
        if (name != null) {
            for (MatchEnum item : MatchEnum.values()) {
                if (item.type.equals(name)) {
                    return item;
                }
            }
        }
        return defaultItem;
    }


}
