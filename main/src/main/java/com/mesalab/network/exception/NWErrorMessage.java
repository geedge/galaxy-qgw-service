package com.mesalab.network.exception;


/**
 * @Author wxs
 * @Date 2022/11/3
 */
public class NWErrorMessage {
    /**
     * 1-2位：异常类型（参数检查异常:01、SQL语句检查异常:02、SQL执行异常:03、引擎计算异常:04、解析异常:05、执行失败:99）。
     * 3-4位：模块 03
     * 5-7：自然排序
     */
    public static String DSL_OBJECT_IS_INVALID = "DSLObject is invalid";
    public static String QUERY_IS_INVALID = "DSLObject.query is invalid";
    public static String QUERY_LIMIT_IS_INVALID = "DSLObject.query.limit is invalid";
    public static String QUERY_OFFSET_IS_INVALID = "DSLObject.query.offset is invalid";
    public static String GRANULARITY_VALUE_IS_INVALID = "DSLObject.query.parameters.Granularity value is invalid";
    public static String MATCH_IS_INVALID = "DSLObject.query.Match is invalid";
    public static String RANGE_TYPE_IS_INVALID = "DSLObject.query.Range type is invalid";
    public static String INTERVALS_TYPE_IS_INVALID = "DSLObject.query.Intervals type is invalid";
    public static String MATCH_FIELD_VALUES_ERROR = "Match fieldValues cannot startWith '*' or endWith '$'";
    public static String RANGE_TYPE_ERROR = "range type is illegal";
    public static String INTERVALS_PARAM_ERROR = "DSLObject.query.query.Intervals is invalid";
    public static String TIME_FORMAT_ERROR = "Time format should follow ISO 8601 standard.";
    public static String INTERVALS_VALUE_ERROR = "Intervals value should be [start, end]";


}
