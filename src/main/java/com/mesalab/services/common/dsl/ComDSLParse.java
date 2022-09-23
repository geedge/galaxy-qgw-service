package com.mesalab.services.common.dsl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.mesalab.cn.common.pojo.DSLObject;
import com.mesalab.cn.common.pojo.DSLParser;
import com.mesalab.knowledge.enums.MatchEnum;
import com.zdjizhi.utils.DateUtils;
import com.zdjizhi.utils.StringUtil;

import java.util.List;

import static java.util.stream.Collectors.joining;

public class ComDSLParse extends DSLParser {


    public static String parseInterval(ComDSLObject.Query query, String defField, boolean format) {
        if (ObjectUtil.isEmpty(query) || ObjectUtil.isEmpty(query.getParameters()) || ObjectUtil.isEmpty(query.getParameters().getIntervals())) {
            return StringUtil.EMPTY;
        }
        List<String> intervals = Splitter.on("/").omitEmptyStrings().splitToList(query.getParameters().getIntervals().get(0));

        if (format) {
            long start = DateUtils.convertStringToTimestamp(intervals.get(0), "yyyy-MM-dd HH:mm:ss");
            long end = DateUtils.convertStringToTimestamp(intervals.get(1), "yyyy-MM-dd HH:mm:ss");
            return Joiner.on(" ").join(" AND ", defField, ">=", start, " AND ", defField, "<", end);
        }
        return Joiner.on(" ").join(" AND ", defField, ">=", intervals.get(0), " AND ", defField, "<", intervals.get(1));
    }


    public static String parseMath(ComDSLObject.Query query, String defField) {
        if (ObjectUtil.isEmpty(query) || ObjectUtil.isEmpty(query.getParameters()) || ObjectUtil.isEmpty(query.getParameters().getMatch())) {
            return StringUtil.EMPTY;
        }
        List<ComDSLObject.Query.FilterBean> matches = query.getParameters().getMatch();
        return matches.stream().map(m -> {
            String fieldKey = ObjectUtil.isEmpty(defField) ? m.getFieldKey() : defField;
            List<String> fieldValues = m.getFieldValues();
            if(MatchEnum.EXACTLY.getType().equals(m.getType())){
                return fieldValues.stream().map(DigestUtil::md5Hex).collect(joining("','", fieldKey.concat(" IN ('"), "')"));
            } else {
                fieldKey = fieldKey.equals(defField) ? "account" : fieldKey;
                return fieldValues.stream().filter(v -> ObjectUtil.isNotEmpty(v)).map(v -> {
                    return parseLike(encode(v), m.getType());
                }).collect(joining(" or ".concat(fieldKey).concat(" LIKE "), fieldKey.concat(" LIKE "), " "));
            }
        }).collect(joining(" AND ", " AND ", " "));
    }

    public static String parseMath(List<ComDSLObject.Query.FilterBean> matches) {
        if (ObjectUtil.isEmpty(matches)) {
            return StringUtil.EMPTY;
        }
        return matches.stream().map(m -> {
            String fieldKey = m.getFieldKey();
            String type = m.getType();
            List<String> fieldValues = m.getFieldValues();
            return fieldValues.stream().filter(v -> ObjectUtil.isNotEmpty(v)).map(v -> {
                return ComDSLParse.parseLike(ComDSLParse.encode(v), type);
            }).collect(joining(" OR ".concat(fieldKey).concat(" LIKE "), fieldKey.concat(" LIKE "), " "));

        }).collect(joining(" OR ", " AND( ", ")"));
    }

    public static String parseRange(List<ComDSLObject.Query.FilterBean> matches) {
        if (ObjectUtil.isNotEmpty(matches)){
            return matches.stream().map(m -> {
                String fieldKey = m.getFieldKey();
                String type = m.getType();
                List<String> fieldValues = m.getFieldValues();
                return fieldValues.stream().filter(v -> ObjectUtil.isNotEmpty(v)).map(v -> {
                    return ComDSLParse.parseRange(ComDSLParse.encode(v), type);
                }).collect(joining(" OR ".concat(fieldKey), fieldKey, " "));

            }).collect(joining(" OR ", " AND( ", ")"));
        }
        return StringUtil.EMPTY;
    }

    @Override
    public String parseSort(DSLObject dslObject, String sql) {
        return null;
    }

    @Override
    public String parseIntervals(DSLObject dslObject, String sql) {
        return null;
    }
    public static String encode(String str){
        return str.replace("'", "\\'");
    }
}
