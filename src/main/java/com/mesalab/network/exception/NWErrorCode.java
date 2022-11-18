package com.mesalab.network.exception;

import lombok.Getter;

/**
 * @Author wxs
 * @Date 2022/11/3
 */
@Getter
public enum NWErrorCode {
    /**
     * 1-2位：异常类型（参数检查异常:01、SQL语句检查异常:02、SQL执行异常:03、引擎计算异常:04、解析异常:05、执行失败:99）。
     * 3-4位：模块 03
     * 5-7：自然排序
     */
    DSL_OBJECT_IS_INVALID("0103001", "DSLObject is invalid"),
    QUERY_IS_INVALID("0103002", "DSLObject.query is invalid"),
    GRANULARITY_VALUE_IS_INVALID("0103003", "DSLObject.query.parameters.Granularity value is invalid"),
    MATCH_IS_INVALID("0103004", "DSLObject.query.Match is invalid"),
    RANGE_TYPE_IS_INVALID("0103005", "DSLObject.query.Range type is invalid"),
    INTERVALS_TYPE_IS_INVALID("0103006", "DSLObject.query.Intervals type is invalid"),
    MATCH_FIELD_VALUES_ERROR("0103007", "Match fieldValues cannot startWith '*' or endWith '$'"),
    RANGE_TYPE_ERROR("0103008", "range type is illegal"),
    INTERVALS_PARAM_ERROR("0103009", "DSLObject.query.query.Intervals is invalid"),
    TIME_FORMAT_ERROR("0103010", "Time format should be: yyyy-MM-dd HH:mm:ss"),
    INTERVALS_VALUE_ERROR("0103011", "Intervals value should be [start, end]"),


    ;
    private String code;
    private String message;

    NWErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
