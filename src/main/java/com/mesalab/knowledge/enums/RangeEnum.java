package com.mesalab.knowledge.enums;

import com.geedgenetworks.utils.StringUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.EnumUtils;

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
@Getter
@AllArgsConstructor
public enum RangeEnum {


    EQ("eq", "==", true),
    NE("ne", "!=", true),
    LT("lt", "<", false),
    LE("le", "<=", false),
    GT("gt", ">", false),
    GE("ge", ">=", false);

    private final String type;
    private final String symbol;
    private final boolean symmetric;


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

    public static String getSymbol(String type){
        return  EnumUtils.getEnum(RangeEnum.class, StringUtil.upperCase(type)).getSymbol();
    }
}