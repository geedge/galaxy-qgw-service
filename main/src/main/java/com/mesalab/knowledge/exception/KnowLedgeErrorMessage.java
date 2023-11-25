package com.mesalab.knowledge.exception;

import lombok.Getter;

/**
 * @Author wxs
 * @Date 2022/11/3
 */
@Getter
public class KnowLedgeErrorMessage {

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


}
