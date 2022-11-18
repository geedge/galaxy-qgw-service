package com.mesalab.cn.exception;

import lombok.Getter;

/**
 * @Author wxs
 * @Date 2022/11/3
 */
@Getter
public enum CNErrorCode {
    /**
     * 1-2位：异常类型（参数检查异常:01、SQL语句检查异常:02、SQL执行异常:03、引擎计算异常:04、解析异常:05、执行失败:99）。
     * 3-4位：模块 01
     * 5-7：自然排序
     */

    DSL_OBJECT_IS_INVALID("0101001", "DSLObject is invalid"),

    QUERY_IS_INVALID("0101002", "DSLObject.query is invalid"),

    DATA_ENGINE_IS_INVALID("0101003", "DSLObject.query.dataEngine is invalid"),

    DATASOURCE_IS_INVALID("0101004", "DSLObject.query.dataSource is invalid"),

    PARAMETERS_IS_INVALID("0101005", "DSLObject.query.parameters is invalid"),

    MATCH_IS_INVALID("0101006", "DSLObject.query.Match is invalid"),

    RANGE_TYPE_IS_INVALID("0101007", "DSLObject.query.Range type is invalid"),

    DISTINCT_TYPE_IS_INVALID("0101008", "DSLObject.query.Distinct type is invalid"),

    INTERVALS_TYPE_IS_INVALID("0101009", "DSLObject.query.Intervals type is invalid"),

    SORT_TYPE_IS_INVALID("0101010", "DSLObject.query.Sort type is invalid"),

    PARAMETERS_MATCH_CONDITION_IS_INVALID("0101011", "DSLObject.query.parameters.match.condition is invalid"),

    PARAMETERS_MATCH_CONDITION_FIELD_VALUE_IS_INVALID("0101012", "DSLObject.query.parameters.match.condition.fieldValue is invalid"),

    PARAMETERS_MATCH_CONDITION_FIELD_KEY_IS_INVALID("0101013", "DSLObject.query.parameters.match.condition.fieldKey is invalid"),

    PARAMETERS_LIMIT_IS_INVALID("0101014", "DSLObject.query.parameters.limit is 0-%s"),

    GRANULARITY_VALUE_IS_INVALID("0101015", "DSLObject.query.parameters.Granularity value is invalid"),

    MATCH_FIELD_VALUES_ERROR("0101016", "Match fieldValues cannot startWith '*' or endWith '$'"),

    INTERVALS_PARAM_ERROR("0101017", "DSLObject.query.query.Intervals is invalid"),

    MATCH_TYPE("0101018", "DSLObject.query.parameters.match.type is invalid"),

    SORT_FIELD_VALUES("0101019", "Sort field values is invalid"),

    RANGE_FIELD_VALUES("0101020", "Range fieldValues cannot null"),

    MATCHING_SIGN("0101021", "error in matching sign. begin with * and do not end with * for suffix matching, end with * and do not begin with $ or * for prefix matching, begin with $ and do not end with * for exactly matching, begin with * and end with * or no wildcard for substring matching"),

    TIME_FORMAT_ERROR("0101022", "Time format should be: yyyy-MM-dd HH:mm:ss"),

    LOGIC_TYPE_IS_INVALID("0101023", "logicType is invalid"),

    INTERVALS_VALUE_ERROR("0101024", "Intervals value should be [start, end]"),

    DISTINCT_TYPE_ERROR("0101025", "distinct type is illegal"),

    RANGE_TYPE_ERROR("0101026", "range type is illegal"),

    MATCH_TYPE_ERROR("0101027", "match type is illegal"),

    // SQL构建错误
    SQL_BUILDER_EXCEPTION("50001100", "Error in SQL Query Builder and Optimizer: [*]"),

    ;


    private String code;
    private String message;

    CNErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
