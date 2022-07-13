package com.mesalab.common.exception;

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
     * 异常代码
     */
    @Builder.Default
    private int errorStatus = ResultStatusEnum.SERVER_ERROR.getCode();

    @Builder.Default
    private String errorCode = ResultCodeEnum.UNKNOW_ERROR.getCode();


    /**
     * 异常信息
     */
    private String errorMessage;

    public BusinessException(String errorMessage) {
        this.errorMessage = errorMessage;
    }


    public BusinessException(String errorMessage, Throwable e) {
        super(errorMessage, e);
    }

    public BusinessException(String errorCode, String errorMessage) {
        super(errorMessage);
        this.errorMessage = errorMessage;
        this.errorCode = errorCode;
    }

    public BusinessException(int errorStatus, String errorCode, String errorMessage, Throwable e) {
        super(errorMessage, e);
        this.errorStatus = errorStatus;
        this.errorCode = errorCode;
    }


}
