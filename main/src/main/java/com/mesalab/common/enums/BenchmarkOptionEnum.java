package com.mesalab.common.enums;

import lombok.Getter;

@Getter
public enum BenchmarkOptionEnum {
    /**
     * 验证SQL数据集
     */
    SQL_VALIDATION("sql_validation"),
    /**
     * 执行SQL测试集
     */
    SQL_EXECUTION("sql_execution");

    private String value;

    BenchmarkOptionEnum(String value) {
        this.value = value;
    }

}
