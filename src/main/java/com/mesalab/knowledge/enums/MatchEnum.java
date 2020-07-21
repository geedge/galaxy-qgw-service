package com.mesalab.knowledge.enums;

import lombok.AllArgsConstructor;

/**
 * 匹配查询
 * <p>
 * type:匹配方式，["exactly"|"prefix"|"suffix"|"substring"|"regex"]
 * <p>
 * exactly:完全匹配
 * prefix:前缀匹配
 * suffix:后缀匹配
 * substring:子串匹配
 * regex:正则符号匹配
 * fieldKey:属性名称，
 * <p>
 * fieldValues:属性值
 * <p>
 * 注：当type=regex时
 * <p>
 * fieldValues值匹配方式符合下列规则，数组内多个值为“或”的关系
 * <p>
 * 匹配方式转义
 * <p>
 * 以*结尾，不以*或者$开始，无论关键字其他位置是否包含表示匹配方式的字符*和$，即表示左匹配(前缀匹配），例如aaaa*bbb$ccc*$*
 * 以*开始，不以*结尾，无论关键字其他位置是否包含表示匹配方式的字符*和$，即表示右匹配（后缀匹配），例如*$*aaa*bbb$ccc
 * 以*开始，以*结尾，无论关键字其他位置是否包含表示匹配方式的字符*和$，即表示子串匹配，例如*aaa$bbb*ccc*
 * 不以*或者$开头，不以*结尾，无论关键字其他位置是否包含表示匹配方式的字符*和$，即表示子串匹配，例如aaa*bbb$ccc*$
 * 以$开头，不以*结尾，无论关键字其他位置是否包含表示匹配方式的字符*和$，即表示完整匹配，例如$aaa*bbb$或者$aaa*$bbb
 */
@AllArgsConstructor
public enum MatchEnum {

    EXACTLY("exactly", "\"{0}\""),
    PREFIX("prefix", "\"{0}%\""),
    SUFFIX("suffix", "\"%{0}\""),
    SUBSTRING("substring", "\"%{0}%\""),
    REGEX("regex", "\"{0}\"");

    private final String type;
    private final String matchExp;

    public String getType() {
        return type;
    }

    public String getMatchExp() {
        return matchExp;
    }


    public static MatchEnum match(String name) {
        if (name != null) {
            for (MatchEnum item : MatchEnum.values()) {
                if (item.type.equals(name)) {
                    return item;
                }
            }
        }
        return null;
    }


}
