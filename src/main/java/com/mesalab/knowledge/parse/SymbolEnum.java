package com.mesalab.knowledge.parse;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum SymbolEnum {

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


    public static SymbolEnum match(String name, SymbolEnum defaultItem) {
        if (name != null) {
            for (SymbolEnum item : SymbolEnum.values()) {
                if (item.type.equals(name)) {
                    return item;
                }
            }
        }
        return defaultItem;
    }
    public static SymbolEnum match(String name) {
        if (name != null) {
            for (SymbolEnum item : SymbolEnum.values()) {
                if (item.type.equals(name)) {
                    return item;
                }
            }
        }
        return null;
    }
}