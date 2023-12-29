package com.mesalab.qgw.exception;

import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.entity.QGWErrorCode;
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
    private int status = ResultStatusEnum.SERVER_ERROR.getCode();

    /**
     * 业务码
     */
    private String code = ResultCodeEnum.UNKNOWN_EXCEPTION.getCode();


    /**
     * 异常信息
     */
    private String message;

    public QGWBusinessException(int status, QGWErrorCode errorCode) {
        super(errorCode.getMessage());
        this.status = status;
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
    }

    public QGWBusinessException(QGWErrorCode errorCode) {
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
