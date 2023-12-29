package com.mesalab.common.enums;

import lombok.Getter;

/**
 * @Date: 2021-03-11 16:05
 * @Author : liuyongqiang
 * @ClassName : MetadataTypeEnum
 * @Description : 元数据类型枚举
 */
@Getter
public enum MetadataType {

    TABLES("tables"),

    FIELDS("fields"),
    ;

    private String value;

    MetadataType(String value) {
        this.value = value;
    }
}
