package com.mesalab.common.enums;

import lombok.Getter;

/**
 * Session枚举
 *
 * @author zhaozhenyao
 * @date 2018/5/10
 */
@Getter
public enum SessionOption {

    /**
     * CURRENT_USER: session中存储的当前用户
     */
    CURRENT_USER("CURRENT_USER"),;

    private String value;

    SessionOption(String value) {
        this.value = value;
    }
}
