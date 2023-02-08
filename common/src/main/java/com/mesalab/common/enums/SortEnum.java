package com.mesalab.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
/*
* @author: tiangaochao
* @create: 2021-08-24
*asc: 升序（默认）
*desc: 降序
* */
@Getter
@AllArgsConstructor
public enum SortEnum {
    DESC("desc"),
    ASC("asc");
    private final String type;
    public static SortEnum getByType(String type) {
        for (SortEnum constants : values()) {
            if (constants.type.equalsIgnoreCase(type)) {
                return constants;
            }
        }
        return null;
    }
}
