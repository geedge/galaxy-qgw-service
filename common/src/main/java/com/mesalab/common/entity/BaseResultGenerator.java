package com.mesalab.common.entity;


import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;

import java.util.Map;

/**
 * BaseResult生成器
 *
 * @author dazzlzy
 * @date 2018/4/1
 */
public class BaseResultGenerator {

    /**
     * 生成返回结果
     *
     * @param status     返回HTTP响应码
     * @param code       返回业务编码
     * @param message    返回消息
     * @param data       返回数据
     * @param formatType 返回数据格式 JSON/CSV
     * @param <T>        返回数据类型
     * @return 返回结果
     */
    public static <T> BaseResult<T> generate(final int status, final String code, final String message, T data, T meta, final Map<String, Object> statistics, final String formatType) {
        return new BaseResult<>(status, code,null, false, message, statistics, formatType, meta, data);
    }

    /**
     * 操作成功响应结果， 默认结果
     *
     * @return 操作成功的默认响应结果
     */
    public static <T> BaseResult<T> success() {
        return new BaseResult<>(ResultStatusEnum.SUCCESS.getCode(), ResultCodeEnum.SUCCESS.getCode(),
                null, true, ResultCodeEnum.SUCCESS.getMessage(), null, null, null, null);
    }

    /**
     * 操作成功响应结果， 自定义数据及信息
     *
     * @param message 自定义信息
     * @param data    自定义数据
     * @param <T>     自定义数据类型
     * @return 响应结果
     */
    public static <T> BaseResult<T> success(final String message, final T data) {
        return new BaseResult<>(ResultStatusEnum.SUCCESS.getCode(), ResultCodeEnum.SUCCESS.getCode(),
                null, true, message, null, null, null, data);
    }

    /**
     * 操作成功响应结果，自定义状态码、自定义信息、数据
     *
     * @param status 自定义状态码
     * @param message 自定义信息
     * @param data    自定义数据
     * @param <T>     自定义数据类型
     * @return 响应结果
     */
    public static <T> BaseResult<T> success(final int status, final String message, final T data) {
        return new BaseResult<>(status, ResultCodeEnum.SUCCESS.getCode(),
                null, true, message, null, null, null, data);
    }

    /**
     * 操作成功响应结果， 自定义数据及信息, 统计结果
     *
     * @param message    自定义信息
     * @param data       自定义数据
     * @param <T>        自定义数据类型
     * @param statistics 统计结果
     * @return 响应结果
     */
    public static <T> BaseResult<T> success(final String message, final T data, final Map<String, Object> statistics) {
        return new BaseResult<>(ResultStatusEnum.SUCCESS.getCode(), ResultCodeEnum.SUCCESS.getCode(),
                null, true, message, statistics, null, null, data);
    }

    /**
     * 操作成功响应结果，自定义数据，默认信息
     *
     * @param data 自定义数据
     * @param <T>  自定义数据类型
     * @return 响应结果
     */
    public static <T> BaseResult<T> success(final T data) {
        return new BaseResult<>(ResultStatusEnum.SUCCESS.getCode(), ResultCodeEnum.SUCCESS.getCode(), null, true,
                ResultCodeEnum.SUCCESS.getMessage(), null, null, null, data);
    }

    /**
     * Desc: 操作成功响应结果，自定义数据，Job结果
     *
     * @param statistics
     * @param job
     * @param data
     * @return {@link BaseResult<T>}
     * @created by wWei
     * @date 2021/11/26 11:46 上午
     */
    public static <T> BaseResult<T> success(final Map<String, Object> statistics, final Map<String, Object> job, final T meta, final T data) {
        return new BaseResult<>(ResultStatusEnum.SUCCESS.getCode(), ResultCodeEnum.SUCCESS.getCode(), null, true,
                ResultCodeEnum.SUCCESS.getMessage(), statistics, job, null, meta, data);
    }


    /**
     * 操作成功响应结果，自定义信息，无数据
     *
     * @param message 自定义信息
     * @return 响应结果
     */
    public static <T> BaseResult<T> success4Message(final String message) {
        return new BaseResult<>(ResultStatusEnum.SUCCESS.getCode(), ResultCodeEnum.SUCCESS.getCode(),
                null, true, message, null, null, null, null);
    }

    /**
     * 操作失败响应结果， 默认结果
     *
     * @return 操作成功的默认响应结果
     */
    public static <T> BaseResult<T> failure() {
        return new BaseResult<>(ResultStatusEnum.BAD_REQUEST.getCode(),
                ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), null, false, ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(), null, null, null, null);
    }

    /**
     * 操作失败响应结果， 自定义错误编码及信息
     *
     * @param status  HTTP状态码
     * @param message 自定义信息
     * @return 响应结果
     */
    public static <T> BaseResult<T> failure(final int status, final String message) {
        return new BaseResult<>(status, ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), null, false, message, null, null, null, null);
    }

    /**
     * 操作失败响应结果， 自定义错误编码及信息
     *
     * @param status  HTTP 状态码
     * @param code    返回业务编码
     * @param message 自定义信息
     * @return 响应结果
     */
    public static <T> BaseResult<T> failure(final int status, final String code, final String message) {
        return new BaseResult<>(status, code, null, false, message, null, null, null, null);
    }

    /**
     * 操作失败响应结果， 自定义错误编码及信息
     *
     * @param status  HTTP 状态码
     * @param code    返回业务编码
     * @param message 自定义信息
     * @return 响应结果
     */
    public static <T> BaseResult<T> failure(final int status, final String code, String queryKey, final String message) {
        return new BaseResult<>(status, code, queryKey, false, message, null, null, null, null);
    }

    /**
     * 操作失败响应结果， 自定义错误编码及信息
     *
     * @param status  HTTP 状态码
     * @param message 自定义信息
     * @return 响应结果
     */
    public static <T> BaseResult<T> failure(final int status, final String message, T data) {
        return new BaseResult<>(status, ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), null, false, message, null, null, null, data);
    }

    /**
     * 操作失败响应结果，自定义错误编码
     *
     * @param resultStatusEnum 自定义错误编码枚举
     * @return 响应结果
     */
    public static <T> BaseResult<T> failure(final ResultStatusEnum resultStatusEnum) {
        return new BaseResult<>(resultStatusEnum.getCode(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), null, false, resultStatusEnum.getMessage(), null, null, null, null);
    }


    /**
     * 操作失败响应结果，自定义信息
     *
     * @param message 自定义信息
     * @param queryKey 唯一标识
     * @return 响应结果
     */
    public static <T> BaseResult<T> failure(final String message, final String queryKey) {

        return new BaseResult<>(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), queryKey, false, message, null, null, null, null);
    }


    /**
     * 异常响应结果， 默认结果
     *
     * @return 操作成功的默认响应结果
     */
    public static <T> BaseResult<T> error() {
        return new BaseResult<>(ResultStatusEnum.SERVER_ERROR.getCode(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), null, false, ResultStatusEnum.SERVER_ERROR.getMessage(), null, null, null, null);
    }

    /**
     * 异常响应结果， 自定义错误编码及信息
     *
     * @param code    自定义错误编码
     * @param message 自定义信息
     * @return 响应结果
     */
    public static <T> BaseResult<T> error(final int code, final String message) {
        return new BaseResult<>(code, ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), null, false, message, null, null, null, null);
    }

    /**
     * 异常响应结果， 自定义错误编码及信息
     *
     * @param code    自定义错误编码
     * @param message 自定义信息
     * @return 响应结果
     */
    public static <T> BaseResult<T> error(final int status, final String code, final String message) {
        return new BaseResult<>(status, code, null, false, message, null, null, null, null);
    }

    /**
     * 异常响应结果，自定义错误编码
     *
     * @param resultStatusEnum 自定义错误编码枚举
     * @return 响应结果
     */
    public static <T> BaseResult<T> error(final ResultStatusEnum resultStatusEnum) {
        return new BaseResult<>(resultStatusEnum.getCode(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), null, false, resultStatusEnum.getMessage(), null, null, null, null);
    }

    /**
     * 业务异常响应结果
     *
     * @param be 业务异常
     * @return 响应结果
     */
    public static <T> BaseResult<T> error(final BusinessException be) {
        return new BaseResult<>(ResultStatusEnum.SERVER_ERROR.getCode(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), null, false, be.getMessage(), null, null, null, null);
    }

    /**
     * 异常响应结果，自定义信息
     *
     * @param message 自定义信息
     * @return 响应结果
     */
    public static <T> BaseResult<T> error(final String message) {
        return new BaseResult<>(ResultStatusEnum.SERVER_ERROR.getCode(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), null, false, message, null, null, null, null);
    }

}
