package com.mesalab.cn.common.constant;

/**
 * @Author wxs
 * @Date 2022/6/30
 */
public class ErrorMessage {

    public static final String DSL_OBJECT_IS_INVALID = "DSLObject is invalid";

    public static final String QUERY_IS_INVALID = "DSLObject.query is invalid";

    public static final String DATA_ENGINE_IS_INVALID = "DSLObject.query.dataEngine is invalid";

    public static final String DATASOURCE_IS_INVALID = "DSLObject.query.dataSource is invalid";

    public static final String PARAMETERS_IS_INVALID = "DSLObject.query.parameters is invalid";

    public static final String MATCH_IS_INVALID = "DSLObject.query.Match is invalid";

    public static final String RANGE_TYPE_IS_INVALID = "DSLObject.query.Range type is invalid";

    public static final String DISTINCT_TYPE_IS_INVALID = "DSLObject.query.Distinct type is invalid";

    public static final String INTERVALS_TYPE_IS_INVALID = "DSLObject.query.Intervals type is invalid";

    public static final String SORT_TYPE_IS_INVALID = "DSLObject.query.Sort type is invalid";

    public static final String PARAMETERS_MATCH_CONDITION_IS_INVALID = "DSLObject.query.parameters.match.condition is invalid";

    public static final String PARAMETERS_MATCH_CONDITION_FIELD_VALUE_IS_INVALID = "DSLObject.query.parameters.match.condition.fieldValue is invalid";

    public static final String PARAMETERS_MATCH_CONDITION_FIELD_KEY_IS_INVALID = "DSLObject.query.parameters.match.condition.fieldKey is invalid";

    public static final String PARAMETERS_LIMIT_IS_INVALID = "DSLObject.query.parameters.limit is 0-%s";

    public static final String GRANULARITY_VALUE_IS_INVALID = "DSLObject.query.parameters.Granularity value is invalid";

    public static final String MATCH_FIELD_VALUES_ERROR = "Match fieldValues cannot startWith '*' or endWith '$'";

    public static final String INTERVALS_PARAM_ERROR = "DSLObject.query.query.Intervals is invalid";

    public static final String DIAGNOSIS_OPTION_ERROR = "Param Syntax Error: The option must be either validation or execution";

    public static final String SCHEMA_PARAM_ERROR = "Schema param error";

    public static final String VISIBILITY_VALUE_ERROR = "Visibility value error";

    public static final String TTL_VALUE_ERROR = "Ttl value error";

    public static final String MATCH_TYPE = "DSLObject.query.parameters.match.type is invalid";

    public static final String SORT_FIELD_VALUES = "Sort field values is invalid";

    public static final String RANGE_FIELD_VALUES = "Range fieldValues cannot null";

    public static final String MATCHING_SIGN = "error in matching sign. begin with * and do not end with * for suffix matching, end with * and do not begin with $ or * for prefix matching, begin with $ and do not end with * for exactly matching, begin with * and end with * or no wildcard for substring matching";

    public static final String NOT_FOUNT_TABLE_NAME = "Can't find table name";

    public static final String ONLY_SUPPORT_SELECT = "Only support statement operation as select";

    public final static String NOT_FOUND = "Not found, check url please";

    public final static String TIME_NOT_NULL = "start_time and end_time need to be filled in at the same time";

    public static final String TIME_FORMAT_ERROR = "Time format should be: yyyy-MM-dd HH:mm:ss";

    public static final String LOGIC_TYPE_IS_INVALID = "logicType is invalid";

    public static final String BUILD_SQL_ERROR = "Build SQL Exception:";

    public static final String SERVICE_BUSY = "The service is busy, please contact the scheduling platform!";

}
