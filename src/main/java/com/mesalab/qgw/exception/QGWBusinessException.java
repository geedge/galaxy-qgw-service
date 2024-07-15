package com.mesalab.qgw.exception;

import com.mesalab.common.enums.HttpStatusCodeEnum;
import com.mesalab.common.exception.CommonErrorCode;
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
public class QGWBusinessException extends RuntimeException {

    /**
     * 状态码
     */
    private int status = HttpStatusCodeEnum.SERVER_ERROR.getCode();

    /**
     * 业务码
     */
    private String code = CommonErrorCode.UNKNOWN_EXCEPTION.getCode();


    /**
     * 异常信息
     */
    private String message;

    public QGWBusinessException(int status, CommonErrorCode errorCode) {
        super(errorCode.getMessage());
        this.status = status;
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
    }

    public QGWBusinessException(CommonErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
    }

    public QGWBusinessException(String code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }


}
