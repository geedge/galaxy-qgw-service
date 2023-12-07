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
     * BAD_REQUEST: 400 服务端无法理解该请求
     * NOT_FOUND： 404 不存在
     * SERVER_ERROR: 500 网络服务异常
     */
    SUCCESS(200, "Success"),
    CREATED(201, "Created"),
    ACCEPTED(202, "Accepted"),
    BAD_REQUEST(400, "Bad request"),
    NOT_FOUND(404, "Not found"),
    REQUEST_URI_TOO_LONG(414, "Request URI too long"),
    REQ_FORBIDDEN(403, "Repeat request"),
    LOCKED(423,"locked"),
    BAD_GATEWAY(502, "Bad gateway"),
    SERVICE_UNAVAILABLE (503,"Service unavailable"),
    GATEWAY_TIMEOUT(504,"Gateway timeout"),
    SERVER_ERROR(500,"Service exception");

    private int code;

    private String message;

    ResultStatusEnum(int code, String message) {
        this.code = code;
    }

}
