package com.mesalab.network.exception;

import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.knowledge.exception.KnowLedgeErrorCode;
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
public class NWBusinessException extends RuntimeException {

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

    public NWBusinessException(int status, NWErrorCode errorCode) {
        super(errorCode.getMessage());
        this.status = status;
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
    }
    public NWBusinessException(NWErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
    }


}
