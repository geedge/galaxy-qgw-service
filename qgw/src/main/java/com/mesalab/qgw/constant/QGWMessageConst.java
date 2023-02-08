package com.mesalab.qgw.constant;

/**
 * TODO
 *
 * @Classname MessageConst
 * @Date 2022/7/1 11:43 上午
 * @Author wWei
 */
public class QGWMessageConst {

    public static final String SCHEMA_ERROR_TTL_VALUE = "Schema error, invalid TTL value.";

    public static final String SCHEMA_ERROR_VISIBILITY_VALUE = "Schema error, invalid visibility value.";

    public static final String SCHEMA_PARAM_ERROR = "Schema param error.";

    public final static String SCHEMA_URL_NOT_FOUND = "Schema URL not found, please check Nacos configurations.";

    public static final String SQL_PARSE_ONLY_SUPPORT_SELECT = "only support select statement operation.";

    public static final String SQL_PARSE_ERROR_NOT_FOUNT_TABLE = "unable to parse table name.";

    public static final String DIAGNOSIS_OPTION_ERROR = "not support option value.";

    public static final String NOT_SUPPORT_DML_PARSER = "not support DML Parser.";

    public static final String GET_TABLE_NAME_ERROR = "Get Table Name Error.";

    public static final String NOT_SUPPORT_SETTING_TTL = "not support setting TTL.";

    public static final String PARAMETER_NOT_OBTAINED = "parameter not obtained.";

    public static final String LOGTYPE_AND_MAXDAYS_NOT_NULL = "logType and maxDays need to be filled in at the same time.";

    public static final String CANNOT_COEXIST_OTHER_TYPES = "When logType exists in ALL, it cannot coexist with other types.";

    public static final String MAXDAYS_ERROR = "logType is not All: maxDays must be numeric and [0-2000]";

    public static final String RESULTID_OR_QUERY_CANNOT_BE_EMPTY = "Param resultId or query cannot be empty.";

    public static final String PASSWORD_OR_SALT_CANNOT_BE_EMPTY = "password or salt cannot be empty.";

    public static final String ES_DIALECT_UNSUPPORTED_EXPLAIN_SYNTAX = "ES dialect unsupported EXPLAIN Syntax.";

    public static final String HBASE_DIALECT_UNSUPPORTED_EXPLAIN_SYNTAX = "Hbase dialect unsupported EXPLAIN Syntax.";

    public static final String ONLY_SUPPORT_STATEMENT_OPERATIONS_FOR_SELECT = "Only support statement operations for select, show and describe.";

    public static final String SQL_MUST_BE_EXISTS = "The SQL must be exists.";

    public static final String OPTION_TYPE_ERROR = "The option must be real-time or long-term or syntax-validation.";

    public static final String MUST_BE_REPORTID_OR_RESULTID = "The option=long-term, must be reportId or resultId.";

    public static final String FORMAT_TYPE_ERROR = "The format must be json or csv.";

    public static final String CUSTOM_FUNCTION_CONVERSION_INSTANCE_EXCEPTION = "Custom function conversion instance exception！";

    public static final String TABLE_NOT_EXIST = "The table is not registered on the platform, please contact the person in charge of the data platform";

    public static final String CHECK_TABLE = "please check tableName.";

    public static final String OPTION_IS_NOT_SUPPORTED = "The option is not supported.";

    public static final String DIALECT_VALUE_ERROR = "Illegal value about dialect.";

    public static final String QUERY_SCHEMA_ERROR = "Get Schema configuration exception.";

    public static final String START_TIME_AND_END_TIME_NOT_NULL = "start_time and end_time need to be filled in at the same time.";

    public static final String QUERY_ID_IS_NOT_SUPPORTED = "queryId is not supported.";

    public static final String SCHEDULED_TASK_ERROR = "Scheduled task data quota setting task execution failed.";

    public static final String DSL_OBJECT_IS_INVALID = "DSLObject is invalid";

    public static final String QUERY_IS_INVALID = "DSLObject.query is invalid";

    public static final String DATA_ENGINE_IS_INVALID = "DSLObject.query.dataEngine is invalid";

    public static final String DATASOURCE_IS_INVALID = "DSLObject.query.dataSource is invalid";

    public static final String RANGE_TYPE_IS_INVALID = "DSLObject.query.Range type is invalid";

    public static final String SORT_TYPE_IS_INVALID = "DSLObject.query.Sort type is invalid";

    public static final String GRANULARITY_VALUE_IS_INVALID = "DSLObject.query.parameters.Granularity value is invalid";

    public static final String INTERVALS_PARAM_ERROR = "DSLObject.query.query.Intervals is invalid";

    public static final String MATCH_TYPE = "DSLObject.query.parameters.match.type is invalid";

    public static final String SORT_FIELD_VALUES = "Sort field values is invalid";

    public static final String RANGE_FIELD_VALUES = "Range fieldValues cannot null";

    public static final String MATCHING_SIGN = "error in matching sign. begin with * and do not end with * for suffix matching, end with * and do not begin with $ or * for prefix matching, begin with $ and do not end with * for exactly matching, begin with * and end with * or no wildcard for substring matching";

    public static final String TIME_FORMAT_ERROR = "Time format should be: [yyyy-MM-dd HH:mm:ss,yyyy-MM-dd'T'HH:mm:ssXXX]";

    public static final String QUERY_TYPE_NOT_NULL = "The param must contain query.type.";

    public static final String QUERY_DATA_SOURCE_NOT_NULL = "The param must contain query.data_source.";

    public static final String QUERY_DATA_SOURCE_ERROR = "The param query.data_source illegal.";


}
