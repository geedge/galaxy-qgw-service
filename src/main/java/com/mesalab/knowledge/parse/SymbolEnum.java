package com.mesalab.knowledge.parse;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum SymbolEnum {

    EQUAL("eq", "=", true),
    NOT_EQUAL("ne", "<>", true),
    LESS_THAN("lt", "<", false),
    LESS_OR_EQUAL("le", "<=", false),
    GREATER_THAN("gt", ">", false),
    GREATER_OR_EQUAL("ge", ">=", false);

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

    /**
     * Returns a Jointype that would produce an equivalent predicate when
     * the arguments to the type are applied swapped.
     *
     * @return a Jointype that would produce an equivalent predicate when
     * the arguments to the type are applied swapped.
     * @throws IllegalStateException if no such equivalent Jointype exists
     */
    public SymbolEnum swapEquivalent() {
        switch (this) {
            case EQUAL:
                return EQUAL;
            case NOT_EQUAL:
                return NOT_EQUAL;
            case LESS_THAN:
                return GREATER_THAN;
            case LESS_OR_EQUAL:
                return GREATER_OR_EQUAL;
            case GREATER_THAN:
                return LESS_THAN;
            case GREATER_OR_EQUAL:
                return LESS_OR_EQUAL;
        }
        throw new IllegalStateException(
                "There is no swap-equivalent " + SymbolEnum.class.getSimpleName() + " to " + this
        );
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
}