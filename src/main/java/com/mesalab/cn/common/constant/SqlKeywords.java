package com.mesalab.cn.common.constant;

/**
 * @Date: 2020-07-22 19:41
 * @Author : liuyongqiang
 * @ClassName : SqlKeywords
 * @Description : SQL关键字常量
 */
public class SqlKeywords {

    public static final String SELECT = " select ";

    public static final String AND = " and ";

    public static final String WHERE = " where ";

    public static final String HAVING = " having ";

    public static final String LIKE = " like ";

    public static final String OR = " or ";

    public static final String FROM = " from ";

    public static final String BWTWEEN = " between ";

    public static final String ORDER_BY = " ORDER BY ";

    public static final String GROUP_BY = " group by ";

    public static final String DESC = " DESC";

    public static final String ASC = " ASC";

    public static final String LIMIT = " limit ";

    public static final String D_TIME = " __time ";

    public static final String D_TIME_FLOOR = " time_floor(__time,''{0}'') ";

    public static final String C_TO_DATE_TIME = "toDateTime(''{0}'')";

    public static final String COUNT_DISTINCT = " COUNT(DISTINCT '{0}')";

    public static final String A_SORT ="SORT";

    public static final String A_TIME = "_TIME";

    public static final String A_FILTER = "FILTER";

    public static final String A_AND = "AND";

    public static final String A_LIKE = "LIKE";

    public static final String A_OR = "OR";

    public static final String A_SPACE = " ";

    public static final String A_LEFT_PARENTHESES = "(";

    public static final String A_RIGHT_PARENTHESES = ")";

    public static final String A_SPOT = ".";

    public static final String A_LIMIT = "LIMIT" ;

    public static final String A_RETURN = "RETURN";

    public static final String C_SECOND = "toDateTime(toUnixTimestamp(toDateTime(toStartOfInterval(toDateTime(common_recv_time),INTERVAL {0} SECOND)))) as granularity";

    public static final String C_MINUTE = "toDateTime(toUnixTimestamp(toDateTime(toStartOfInterval(toDateTime(common_recv_time),INTERVAL {0} MINUTE)))) as granularity";

    public static final String C_HOUR = "toDateTime(toUnixTimestamp(toDateTime(toStartOfInterval(toDateTime(common_recv_time),INTERVAL {0} HOUR)))) as granularity";

    public static final String C_DAY = "toDateTime(toUnixTimestamp(toDateTime(toStartOfInterval(toDateTime(common_recv_time),INTERVAL {0} DAY)))) as granularity";

    public static final String C_WEEK = "toDateTime(toUnixTimestamp(toDateTime(toStartOfInterval(toDateTime(common_recv_time),INTERVAL {0} WEEK)))) as granularity";

    public static final String C_MONTH = "toDateTime(toUnixTimestamp(toDateTime(toStartOfInterval(toDateTime(common_recv_time),INTERVAL {0} MONTH)))) as granularity";

    public static final String C_YEAR = "toDateTime(toUnixTimestamp(toDateTime(toStartOfInterval(toDateTime(common_recv_time),INTERVAL {0} YEAR)))) as granularity";



}
