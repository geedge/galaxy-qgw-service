package com.mesalab.cn.common.pojo;

import com.mesalab.cn.common.constant.SqlKeywords;
import com.mesalab.cn.common.enums.MatchTypeEnum;
import com.mesalab.cn.service.CNService;
import com.zdjizhi.utils.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;

/**
 * @Date: 2020-07-22 17:35
 * @Author : liuyongqiang
 * @ClassName : DslParser
 * @Description : DSLParse解析抽象类
 */
@Component
public abstract class DSLParser {

    //默认数据大小
    private final static String DEFAULT_LIMIT = "100000";


    @Autowired
    CNService druidEngine;



    /**
     * 解析 Sort对象
     */
    public abstract String parseSort(DSLObject dslObject, String sql);

    /**
     * 解析 Intervals对象
     */
    public abstract String parseIntervals(DSLObject dslObject, String sql);

    /**
     * @param dslObject:
     * @param sql:
     * @Description: 解析 Match对象
     * @Author: liuyongqiang
     * @Date: 2020/7/23 17:45
     * @return: java.lang.String
     **/
    public String parseMatch(DSLObject dslObject, String sql) {
        if (!Objects.nonNull(dslObject.getQuery().getParameters().getMatch())) {
            return sql;
        }
        if (CollectionUtils.isEmpty(dslObject.getQuery().getParameters().getMatch().getCondition())) {
            return sql;
        }
        List<Condition> conditionList = dslObject.getQuery().getParameters().getMatch().getCondition();
        String logicType = dslObject.getQuery().getParameters().getDistinct().getLogicType();
        if (StringUtil.isBlank(logicType) || logicType.equalsIgnoreCase(SqlKeywords.AND)) {
            logicType = SqlKeywords.AND;
        } else {
            logicType = SqlKeywords.OR;
        }
        int index = 0;
        for (Condition condition : conditionList) {
            index++;
            String type = condition.getType();
            String fieldKey = condition.getFieldKey();
            List<Object> values = condition.getFieldValues();

            if (CollectionUtils.isEmpty(values)) {
                break;
            }

            String start;
            if (index == 0) {
                start = SqlKeywords.AND + " (";
            } else {
                start = logicType;
            }
            String join = index == 1 ? SqlKeywords.WHERE + " (" : start;
            sql = sql.concat(join)
                    .concat(fieldKey)
                    .concat(SqlKeywords.LIKE)
                    .concat(parseLike(values.get(0).toString(), type));

            if (values.size() > 1) {
                for (int i = 1; i < values.size(); i++) {
                    sql = sql.concat(SqlKeywords.OR)
                            .concat(fieldKey)
                            .concat(SqlKeywords.LIKE)
                            .concat(parseLike(values.get(i).toString(), type));
                }
            }
        }
        if (index >= 1) {
            sql = sql + ")";
        }
        return sql;
    }

    /**
     * @param dslObject:
     * @param sql:
     * @Description: 解析Limit对象
     * @Author: liuyongqiang
     * @Date: 2020/7/23 17:44
     * @return: java.lang.String
     **/
    public String parseLimit(DSLObject dslObject, String sql) {
        sql = sql.concat(SqlKeywords.LIMIT);
        if (StringUtil.isNotEmpty(dslObject.getQuery().getParameters().getLimit())) {
            sql = sql.concat(dslObject.getQuery().getParameters().getLimit().toString());
        } else {
            sql = sql.concat(DEFAULT_LIMIT);
        }
        return sql;
    }

    /**
     * @param value:
     * @param type:
     * @Description: match.type转换
     * @Author: liuyongqiang
     * @Date: 2020/7/23 11:47
     * @return: java.lang.String
     **/
    public String parseLike(String value, String type) {
        switch (MatchTypeEnum.getByType(type)) {
            case REGEX:
                return parseRegex(value);//正则匹配
            case PREFIX:
                return "'" + value + "%'";//前缀匹配
            case SUFFIX:
                return "'%" + value + "'";//后缀匹配
            case EXACTLY:
                return "'" + value + "'";//完全匹配
            case SUBSTRING:
                return "'%" + value + "%'";//子串匹配
        }
        return "";
    }

    /**
     * @param value:
     * @Description: parseRegex
     * @Author: liuyongqiang
     * @Date: 2020/7/23 11:49
     * @return: java.lang.String
     **/
    public String parseRegex(String value) {
        if (value.startsWith("$")) {
            //以$开头，不以*结尾，无论关键字其他位置是否包含表示匹配方式的字符*和$，即表示完整匹配
            value = value.replaceAll("\\$", "");
            return "'" + value + "'";
        } else if (value.startsWith("*") && value.endsWith("*")) {
            //以*开始，以*结尾，无论关键字其他位置是否包含表示匹配方式的字符*和$，即表示子串匹配
            value = value.replaceAll("\\*", "");
            return "'%" + value + "%'";
        } else if (value.startsWith("*")) {
            //以*开始，不以*结尾，无论关键字其他位置是否包含表示匹配方式的字符*和$，即表示右匹配（后缀匹配）
            value = value.replaceAll("\\*", "");
            return "'%" + value + "'";
        } else if (value.endsWith("*")) {
            //以*结尾，不以*或者$开始，无论关键字其他位置是否包含表示匹配方式的字符*和$，即表示左匹配(前缀匹配)
            value = value.replaceAll("\\*", "");
            return "'" + value + "%'";
        } else {
            //不以*或者$开头，不以*结尾，无论关键字其他位置是否包含表示匹配方式的字符*和$，即表示子串匹配
            return "'%" + value + "%'";
        }
    }

}
