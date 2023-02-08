package com.measlab.tsg.exception;

import lombok.Getter;

/**
 * @Author wxs
 * @Date 2022/11/3
 */
@Getter
public class TSGErrorMessage {

    public static String NOT_SUPPORT = "Not Support";

    public static String QUERY_IS_INVALID = "query is invalid";

    public static String DATASOURCE_IS_INVALID = "dataSource is invalid";

    public static String DATASOURCE_IS_ERROR = "dataSource no match";

    public static String MATCH_TYPE_ERROR = "match type is illegal";

    public static String MATCHING_SIGN = "error in matching sign. begin with * and do not end with * for suffix matching, end with * and do not begin with $ or * for prefix matching, begin with $ and do not end with * for exactly matching, begin with * and end with * or no wildcard for substring matching";

    public static String RANGE_TYPE_ERROR = "range type is illegal";

    public static String SORT_TYPE_ERROR = "sort type is illegal";

    public static String INTERVALS_IS_INVALID = "intervals should be [startTime/endTime],such as 2020-01-01 00:00:00/2020-02-02 00:00:00";

    public static String TIME_ERROR = "the end time must be after the start time";

    public static String LIMIT_IS_ILLEGAL = "limit is illegal";

    public static String LIMIT_IS_ERROR = "limit is greater than 0 and less than %s";

    public static String FIELD_VALUES_IS_INVALID = "fieldValues of depth is null";

    public static String FIELD_VALUES_FORMAT_ERROR = "fieldValues of depth is illegal, you should type in an integer greater than 0";

    public static String DSL_OBJECT_IS_INVALID = "DSLObject is invalid";

    public static String DATA_ENGINE_IS_INVALID = "DSLObject.query.dataEngine is invalid";

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

    public static String QUERY_LIMIT_IS_INVALID = "DSLObject.query.limit is invalid";

    public static String QUERY_OFFSET_IS_INVALID = "DSLObject.query.offset is invalid";

    public static String GRANULARITY_VALUE_IS_INVALID = "DSLObject.query.parameters.Granularity value is invalid";

    public static String MATCH_FIELD_VALUES_ERROR = "Match fieldValues cannot startWith '*' or endWith '$'";

    public static String INTERVALS_PARAM_ERROR = "DSLObject.query.query.Intervals is invalid";


}
