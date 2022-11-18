package com.mesalab.cn.entity.pojo;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.base.Joiner;
import com.mesalab.cn.constant.ErrorMessage;
import com.mesalab.cn.constant.SqlKeywords;
import com.mesalab.cn.enums.RangeTypeEnum;
import com.mesalab.cn.exception.CNBusinessException;
import com.mesalab.cn.exception.CNErrorCode;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.zdjizhi.utils.StringUtil;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;


/**
 * @Date: 2020-07-20 18:10
 * @Author : wangwei
 * @ClassName : CNParser
 * @Description :  CNParser
 */

@Component
public class CNParser extends DSLParser {

    private static final Log log = LogFactory.get();

    public final static String UA_FILTER = "osList,browserCnt,deviceCnt,osCnt,terminalCatCnt,deviceList,terminalCatList,terminalList,terminalCnt,browserList";

    public String buildShareNetSQL(DSLObject dslObject) {
        String selectSql;
        try {
            DSLObject.Query.Parameters parameters = dslObject.getQuery().getParameters();
            selectSql = buildSelectFromSql(dslObject, parameters);
            selectSql = parseQueryOfShareNet(dslObject, selectSql);
        } catch (RuntimeException e) {
            log.error("Build SQL Exception:{}",e);
            throw new CNBusinessException(ResultStatusEnum.SERVER_ERROR.getCode(), CNErrorCode.SQL_BUILDER_EXCEPTION.getCode(),
                    String.format(CNErrorCode.SQL_BUILDER_EXCEPTION.getMessage(),e.getMessage()));
        }
        return selectSql;
    }


    public String bannedAppSQL(DSLObject dslObject) {
        String selectSql;
        try {
            DSLObject.Query.Parameters parameters = dslObject.getQuery().getParameters();
            selectSql = buildSelectFromSql(dslObject, parameters);
            selectSql = parseQueryOfBannedApp(dslObject, selectSql);
        } catch (RuntimeException e) {
            log.error("Build SQL Exception:{}",e);
            throw new CNBusinessException(ResultStatusEnum.SERVER_ERROR.getCode(), CNErrorCode.SQL_BUILDER_EXCEPTION.getCode(),
                    String.format(CNErrorCode.SQL_BUILDER_EXCEPTION.getMessage(),e.getMessage()));
        }
        return selectSql;
    }


    public String buildSelectFromSql(DSLObject dslObject, DSLObject.Query.Parameters parameters) {
        if (StringUtil.isNotEmpty(parameters.getDistinct()) && !CollectionUtils.isEmpty(parameters.getDistinct().getCondition())) {
            return buildSelectFromSqlOfDistinct(parameters, dslObject.getQuery().getDataSource());
        }
        if (StringUtil.isNotEmpty(parameters.getRange()) && !CollectionUtils.isEmpty(parameters.getRange().getCondition())) {
            return buildSelectFromSqlOfRange(parameters, dslObject.getQuery().getDataSource());
        }
        throw new BusinessException("build SQL Unsupported: SELECT FROM ");
    }

    private String parseQueryOfBannedApp(DSLObject dslObject, String sql) {
        sql = parseIntervals(dslObject, sql);
        sql = parseMatch(dslObject, sql);
        sql = parseGroup(dslObject, sql);
        sql = parseHaving(dslObject.getQuery().getParameters().getRange(), sql);
        sql = parseSort(dslObject, sql);
        log.info("Parse Query SQL:{}", sql);
        return sql;
    }

    public String parseQueryOfShareNet(DSLObject dslObject, String sql) {
        sql = parseIntervals(dslObject, sql);
        sql = parseMatch(dslObject, sql);
        sql = parseRange(dslObject, sql);
        sql = parseGroup(dslObject, sql);
        sql = parseSort(dslObject, sql);
        log.info("Parse Query SQL:{}", sql);
        return sql;
    }

    public String buildSelectFromSqlOfRange(DSLObject.Query.Parameters parameters, String tableName) {
        if (StringUtil.isEmpty(parameters.getRange()) || CollectionUtils.isEmpty(parameters.getRange().getCondition())) {
            return StringUtil.EMPTY;
        }
        List<Condition> rangeCondition = parameters.getRange().getCondition();
        if (CollectionUtils.isEmpty(rangeCondition)) {
            return StringUtil.EMPTY;
        }
        HashMap<String, String> rangeKey = new HashMap<>(16);
        for (Condition condition : rangeCondition) {
            if (UA_FILTER.contains(condition.getFieldKey())) {
                continue;
            }
            String fieldKey = condition.getFieldKey();
            rangeKey.put(fieldKey, MessageFormat.format("SUM({0}) as {1}", fieldKey, fieldKey));
        }
        return MessageFormat.format("SELECT user_id, ip, {0} FROM {1} ", Joiner.on(",").join(rangeKey.values()), tableName);
    }

    private String buildSelectFromSqlOfDistinct(DSLObject.Query.Parameters parameters, String tableName) {
        if (StringUtil.isEmpty(parameters.getDistinct()) || CollectionUtils.isEmpty(parameters.getDistinct().getCondition())) {
            return StringUtil.EMPTY;
        }
        List<Condition> distinctCondition = parameters.getDistinct().getCondition();
        String[] groupBy = parameters.getGranularity().split(",");

        HashMap<String, String> distinctKey = new HashMap<>(16);

        for (Condition condition : distinctCondition) {
            if (UA_FILTER.contains(condition.getFieldKey())) {
                continue;
            }
            String fieldKey = condition.getFieldKey();
            distinctKey.put(fieldKey, MessageFormat.format("APPROX_COUNT_DISTINCT({0}) as {1}UniqueCnt", fieldKey, fieldKey));
        }
        if (distinctKey.isEmpty()) {
            return StringUtil.EMPTY;
        }

        return MessageFormat.format("SELECT {0}, {1}, {2}, SUM(hits) AS hits FROM {3} ",
                Joiner.on(",").join(groupBy),
                Joiner.on(",").join(distinctKey.values()),
                Joiner.on(",").join(distinctKey.keySet()),
                tableName);
    }

    public String parseHaving(DSLObject.Query.Parameters.Range range, String sql) {
        if (StringUtil.isEmpty(range)) {
            return sql;
        }
        if (CollectionUtils.isEmpty(range.getCondition())) {
            return sql;
        }
        List<Condition> conditionList = range.getCondition();
        String logicType = range.getLogicType();
        if (StringUtil.isBlank(logicType) || SqlKeywords.AND.trim().equalsIgnoreCase(logicType)) {
            logicType = SqlKeywords.AND;
        } else {
            logicType = SqlKeywords.OR;
        }
        int count = 0;
        for (Condition condition : conditionList) {
            String type = condition.getType();
            String fieldKey = condition.getFieldKey();
            List<Object> values = condition.getFieldValues();

            if (CollectionUtils.isEmpty(values)) {
                break;
            }

            String join;
            if (count++ == 0) {
                join = SqlKeywords.HAVING.concat(" ( ");
            } else {
                join = logicType.concat(" ( ");
            }
            String expr = RangeTypeEnum.getByType(type).getExpr();

            sql = sql.concat(join)
                    .concat(fieldKey)
                    .concat(expr)
                    .concat(values.get(0).toString());

            if (values.size() > 1) {
                for (int i = 1; i < values.size(); i++) {
                    sql = sql.concat(SqlKeywords.OR)
                            .concat(fieldKey)
                            .concat(expr)
                            .concat(values.get(i).toString());
                }
            }
            sql = sql.concat(")");
        }
        return sql;
    }

    public String parseRange(DSLObject dslObject, String sql) {
        if (!Objects.nonNull((dslObject.getQuery().getParameters().getRange()))) {
            return sql;
        }
        if (CollectionUtils.isEmpty(dslObject.getQuery().getParameters().getRange().getCondition())) {
            return sql;
        }
        List<Condition> conditionList = dslObject.getQuery().getParameters().getRange().getCondition();
        String logicType = dslObject.getQuery().getParameters().getRange().getLogicType();
        if (StringUtil.isBlank(logicType) || logicType.equalsIgnoreCase(SqlKeywords.AND.trim())) {
            logicType = SqlKeywords.AND;
        } else {
            logicType = SqlKeywords.OR;
        }
        int count = 0;
        for (Condition condition : conditionList) {
            String type = condition.getType();
            String fieldKey = condition.getFieldKey();
            List<Object> values = condition.getFieldValues();

            if (CollectionUtils.isEmpty(values)) {
                break;
            }
            String start;
            if (count++ == 0) {
                start = SqlKeywords.AND.concat(" ( ");
            } else {
                start = logicType.concat(" ( ");
            }
            String join = sql.contains(SqlKeywords.WHERE) ? start : SqlKeywords.WHERE + " (";
            String expr = RangeTypeEnum.getByType(type).getExpr();
            sql = sql.concat(join).concat(fieldKey).concat(expr).concat(values.get(0).toString());

            if (values.size() > 1) {
                for (int i = 1; i < values.size(); i++) {
                    sql = sql.concat(SqlKeywords.OR)
                            .concat(fieldKey)
                            .concat(expr)
                            .concat(values.get(i).toString());
                }
            }
            sql = sql.concat(")");
        }
        return sql;
    }

    public String parseGroup(DSLObject dslObject, String sql) {
        DSLObject.Query.Parameters parameters = dslObject.getQuery().getParameters();
        String granularity = parameters.getGranularity();
        if (StringUtil.isBlank(granularity) && StringUtil.isEmpty(dslObject.getQuery().getParameters().getDistinct())) {
            return sql;
        }
        if (StringUtil.isEmpty(parameters.getDistinct())
                && StringUtil.isEmpty(parameters.getRange())) {
            return sql + " group by " + granularity;
        }
        List<String> distinctKey = new ArrayList<>();
        if (StringUtil.isNotEmpty(parameters.getDistinct()) && !CollectionUtils.isEmpty(parameters.getDistinct().getCondition())) {
            List<Condition> distinctCondition = parameters.getDistinct().getCondition();
            for (Condition condition : distinctCondition) {
                if (UA_FILTER.contains(condition.getFieldKey())) {
                    continue;
                }
                distinctKey.add(String.valueOf(condition.getFieldKey()));
            }
        }
        if (CollectionUtils.isEmpty(distinctKey)) {
            return MessageFormat.format("{0} GROUP BY {1} ", sql, granularity);
        }
        return MessageFormat.format("{0} GROUP BY {1}, {2} ", sql, granularity, Joiner.on(",").join(distinctKey));
    }

    @Override
    public String parseSort(DSLObject dslObject, String sql) {
        List<DSLObject.Query.Parameters.Sort> list;
        if (CollectionUtils.isEmpty(list = dslObject.getQuery().getParameters().getSort())) {
            return sql;
        }

        List<String> sortList = new ArrayList<>();
        for (DSLObject.Query.Parameters.Sort sort : list) {
            if (UA_FILTER.contains(sort.getFieldKey())) {
                continue;
            }
            String temp = sort.getFieldKey().concat(SqlKeywords.A_SPACE).concat(
                    StringUtil.isBlank(sort.getType()) ? SqlKeywords.ASC : sort.getType());
            sortList.add(temp);
        }
        if(sortList.isEmpty()){
            return sql;
        }
        sql = sql.concat(SqlKeywords.ORDER_BY
                .concat(Joiner.on(",").join(sortList)));
        return sql;
    }

    @Override
    public String parseIntervals(DSLObject dslObject, String sql) {
        //缺失时，默认为当前时间为止一天内的数据
        if (CollectionUtils.isEmpty(dslObject.getQuery().getParameters().getIntervals())) {
            return sql.concat(SqlKeywords.WHERE).concat(" (__time >= CURRENT_TIMESTAMP - INTERVAL '1' DAY ) ");
        }
        List<String> intervals = dslObject.getQuery().getParameters().getIntervals();
        for (String intervalsBean : intervals) {
            String[] split = intervalsBean.split("/");

            if (CollectionUtils.isEmpty(intervals)) {
                break;
            }

            String join = sql.contains(SqlKeywords.WHERE) ?
                    SqlKeywords.AND + SqlKeywords.A_LEFT_PARENTHESES
                    : SqlKeywords.WHERE + SqlKeywords.A_LEFT_PARENTHESES;

            sql = sql.concat(join).concat("__time")
                    .concat(RangeTypeEnum.GE.getExpr())
                    .concat("TIMESTAMP '" + split[0] + "' AND __time")
                    .concat(RangeTypeEnum.LT.getExpr())
                    .concat("TIMESTAMP '" + split[1] + "'");
        }
        return sql.concat(SqlKeywords.A_RIGHT_PARENTHESES);
    }
}