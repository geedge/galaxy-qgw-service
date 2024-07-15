package com.mesalab.common.enums;

import lombok.Getter;

/**
 * Cookie枚举
 *
 * @author dazzlzy
 * @date 2018/5/26
 */
@Getter
public enum CookieOption {
    /**
     * REMEMBER_ME： Cookie中存储的REMEMBER_ME
     */
    REMEMBER_ME("rememberMe"),;

    private String value;

    CookieOption(String value) {
        this.value = value;
    }
}
