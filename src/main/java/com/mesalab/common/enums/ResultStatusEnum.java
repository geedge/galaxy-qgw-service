package com.mesalab.common.enums;

import lombok.Getter;

/**
 * BaseResult的http状态编码枚举
 *
 * @author
 * @date 2018/3/21
 */
@Getter
public enum ResultStatusEnum {
    /**
     * SUCCESS: 200 成功
     * FAIL: 400 失败
     * NOT_FOUND： 404 不存在
     * SERVER_ERROR: 500 网络服务异常
     */
    SUCCESS(200, "成功"),
    CREATED(201, "创建"),
    FAIL(400, "失败"),
    NOT_FOUND(404, "不存在"),
    REQUEST_URI_TOO_LONG(414, "请求URI太长"),
    REQ_FORBIDDEN(403, "重复请求"),
    SERVER_ERROR(500, "服务异常");

    private int code;

    private String message;

    ResultStatusEnum(int code, String message) {
        this.code = code;
    }

}
