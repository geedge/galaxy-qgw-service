package com.mesalab.common.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.mesalab.common.enums.HttpStatusCodeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 响应结果
 * 如果使用注解@JsonInclude(JsonInclude.Include.NON_NULL)： 则会保证序列化json的时候,如果是null的对象,key也会消失
 *
 * @author dazzlzy
 * @date 2018/3/21
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({"status", "message", "requestId", "success", "code", "job", "output_mode", "statistics", "meta", "data"})
public class BaseResult<T> implements Serializable {

    private Integer status;

    private String code;

    @JsonProperty("request_id")
    private String requestId;

    private Boolean success;

    private String message;

    private Map<String, Object> statistics;

    private Map<String, Object> job;

    @JsonProperty("output_mode")
    private String outputMode;

    private T meta;

    private T data;

    public BaseResult(Integer status, String code, String requestId, boolean success, String message, Map<String, Object> statistics, String outputMode, T meta, T data) {
        this.status = status;
        this.code = code;
        this.requestId = requestId;
        this.success = success;
        this.message = message;
        this.statistics = statistics;
        this.outputMode = outputMode;
        this.meta = meta;
        this.data = data;
    }

    /**
     * 判断是否是成功结果
     * JsonIgnore使之不在json序列化结果当中
     *
     * @return 是否为成功结果
     */
    public boolean isSuccess() {
        return this.status >= HttpStatusCodeEnum.SUCCESS.getCode() && this.status < 300;
    }

    @Override
    public String toString() {
        return "BaseResult{" +
                "status=" + status +
                ", code='" + code + '\'' +
                ", requestId='" + requestId + '\'' +
                ", success=" + success +
                ", message='" + message + '\'' +
                ", statistics=" + statistics +
                ", job=" + job +
                ", output_mode='" + outputMode + '\'' +
                ", meta=" + meta +
                ", data=" + data +
                '}';
    }
}