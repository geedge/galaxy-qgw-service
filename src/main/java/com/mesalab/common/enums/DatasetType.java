package com.mesalab.common.enums;

import lombok.Getter;

/**
 * TODO
 *
 * @Classname DatasetType
 * @Date 2024/10/14 17:41
 * @Author wWei
 */
@Getter
public enum DatasetType {
    SQL("sql"),
    DSL("dsl");
    private String value;

    DatasetType(String value) {
        this.value = value;
    }
}
