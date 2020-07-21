package com.mesalab.knowledge.enums;

import lombok.AllArgsConstructor;

/**
 * 范围查询
 * <p>
 * type:["gt"|"lt"|"eq"|"ge"|"le"|"ne"]
 * <p>
 * gt: > 大于（greater than）
 * lt: < 小于（less than）
 * eq: = 等于（equal to）
 * ge: >= 大于等于（greater than or equal）
 * le: <= 小于等于（less than or equal）
 * ne: != 不等于（not equal）
 */
@AllArgsConstructor
public enum RangeEnum {


    EQ("eq", "==", true),
    NE("ne", "<>", true),
    LT("lt", "<", false),
    LE("le", "<=", false),
    GT("gt", ">", false),
    GE("ge", ">=", false);

    private final String type;
    private final String symbol;
    private final boolean symmetric;

    public String type() {
        return type;
    }

    public String symbol() {
        return symbol;
    }

    /**
     * Returns if this operation is symmetric with respect to its parameters.
     * More formally returns true if: A oper B is true if and only if B oper A is
     * true.
     *
     * @return if this operation is symmetric with respect to its parameters
     */
    public boolean isSymmetric() {
        return symmetric;
    }

    public static RangeEnum match(String name) {
        if (name != null) {
            for (RangeEnum item : RangeEnum.values()) {
                if (item.type.equals(name)) {
                    return item;
                }
            }
        }
        return null;
    }
}