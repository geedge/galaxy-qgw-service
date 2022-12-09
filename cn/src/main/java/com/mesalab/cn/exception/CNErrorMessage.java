package com.mesalab.cn.exception;

/**
 * @Author wxs
 * @Date 2022/11/21
 */
public class CNErrorMessage {
    public static String DSL_OBJECT_IS_INVALID = "DSLObject is invalid";

    public static String QUERY_IS_INVALID = "DSLObject.query is invalid";

    public static String DATA_ENGINE_IS_INVALID = "DSLObject.query.dataEngine is invalid";

    public static String DATASOURCE_IS_INVALID = "DSLObject.query.dataSource is invalid";

    public static String PARAMETERS_IS_INVALID = "DSLObject.query.parameters is invalid";

    public static String MATCH_IS_INVALID = "DSLObject.query.Match is invalid";

    public static String RANGE_TYPE_IS_INVALID = "DSLObject.query.Range type is invalid";

    public static String DISTINCT_TYPE_IS_INVALID = "DSLObject.query.Distinct type is invalid";

    public static String INTERVALS_TYPE_IS_INVALID = "DSLObject.query.Intervals type is invalid";

    public static String SORT_TYPE_IS_INVALID = "DSLObject.query.Sort type is invalid";

    public static String PARAMETERS_MATCH_CONDITION_IS_INVALID = "DSLObject.query.parameters.match.condition is invalid";

    public static String PARAMETERS_MATCH_CONDITION_FIELD_VALUE_IS_INVALID = "DSLObject.query.parameters.match.condition.fieldValue is invalid";

    public static String PARAMETERS_MATCH_CONDITION_FIELD_KEY_IS_INVALID = "DSLObject.query.parameters.match.condition.fieldKey is invalid";

    public static String PARAMETERS_LIMIT_IS_INVALID = "DSLObject.query.parameters.limit is 0-%s";

    public static String TIME_FORMAT_ERROR = "Time format should be: yyyy-MM-dd HH:mm:ss";

    public static String LOGIC_TYPE_IS_INVALID = "logicType is invalid";

    public static String INTERVALS_VALUE_ERROR = "Intervals value should be [start, end]";

    public static String DISTINCT_TYPE_ERROR = "distinct type is illegal";

    public static String RANGE_TYPE_ERROR = "range type is illegal";

    public static String MATCH_TYPE_ERROR = "match type is illegal";

}
