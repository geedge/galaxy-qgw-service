package com.mesalab.knowledge.exception;

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
public class KnowledgeBusinessException extends RuntimeException {

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

    public KnowledgeBusinessException(int status, KnowLedgeErrorCode errorCode) {
        super(errorCode.getMessage());
        this.status = status;
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
    }
    public KnowledgeBusinessException(KnowLedgeErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
    }
    public KnowledgeBusinessException(String code, String message) {
        super(message);
        this.message = message;
        this.code = code;
    }

}
