package com.mesalab.services.exception;

import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import lombok.*;

/**
 * 业务异常
 *
 * @author dazzlzy
 * @date 2018/3/22
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessException extends RuntimeException {

    /**
     * 状态码
     */
    @Builder.Default
    private int status = ResultStatusEnum.SERVER_ERROR.getCode();

    /**
     * 业务码
     */
    @Builder.Default
    private String code = ResultCodeEnum.UNKNOW_EXCEPTION.getCode();


    /**
     * 异常信息
     */
    private String message;

    public BusinessException(String message) {
        super(message);
        this.message = message;
    }


    public BusinessException(String message, Throwable e) {
        super(message, e);
    }

    public BusinessException(String code, String message) {
        super(message);
        this.message = message;
        this.code = code;
    }

    public BusinessException(int status, String code, Throwable e) {
        super(e);
        this.status = status;
        this.code = code;
    }


}
