package com.mesalab.knowledge.exception;

import lombok.Getter;

/**
 * @Author wxs
 * @Date 2022/11/3
 */
@Getter
public enum KnowLedgeErrorCode {
    /**
     * 1-2位：异常类型（参数检查异常:01、SQL语句检查异常:02、SQL执行异常:03、引擎计算异常:04、解析异常:05、执行失败:99）。
     * 3-4位：模块 02
     * 5-7：自然排序
     */

    NOT_SUPPORT("0102001", "Not Support"),
    QUERY_IS_INVALID("0102002", "query is invalid"),
    DATASOURCE_IS_INVALID("0102003", "dataSource is invalid"),
    DATASOURCE_IS_ERROR("0102004", "dataSource no match"),
    MATCH_TYPE_ERROR("0102005", "match type is illegal"),
    MATCHING_SIGN("0102006", "error in matching sign. begin with * and do not end with * for suffix matching, end with * and do not begin with $ or * for prefix matching, begin with $ and do not end with * for exactly matching, begin with * and end with * or no wildcard for substring matching"),
    RANGE_TYPE_ERROR("0102007", "range type is illegal"),
    SORT_TYPE_ERROR("0102008", "sort type is illegal"),
    INTERVALS_IS_INVALID("0102009", "intervals should be [startTime/endTime],such as 2020-01-01 00:00:00/2020-02-02 00:00:00"),
    TIME_ERROR("0102010", "the end time must be after the start time"),
    LIMIT_IS_ILLEGAL("0102011", "limit is illegal"),
    LIMIT_IS_ERROR("0102012", "limit is greater than 0 and less than %s"),
    FIELD_VALUES_IS_INVALID("0102013", "fieldValues of depth is null"),
    FIELD_VALUES_FORMAT_ERROR("0102014", "fieldValues of depth is illegal, you should type in an integer greater than 0"),
    // 未知异常
    UNKNOWN_EXCEPTION("55501999", "Unknown exception:%s");


    ;
    private String code;
    private String message;

    KnowLedgeErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
