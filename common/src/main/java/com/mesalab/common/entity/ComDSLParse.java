package com.mesalab.common.entity;

import cn.hutool.core.util.ObjectUtil;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
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

    public static String parseMath(List<ComDSLObject.Query.FilterBean> matches) {
        if (ObjectUtil.isEmpty(matches)) {
            return StringUtil.EMPTY;
        }
        return matches.stream().map(m -> {
            String fieldKey = m.getFieldKey();
            String type = m.getType();
            List<String> fieldValues = m.getFieldValues();
            return fieldValues.stream().filter(v -> ObjectUtil.isNotEmpty(v)).map(v -> {
                return ComDSLParse.parseMatch(ComDSLParse.encode(v), type);
            }).collect(joining(" OR ".concat(fieldKey), fieldKey, " "));

        }).collect(joining(" ) AND ( ", " AND( ", ")"));
    }

    public static String parseRange(List<ComDSLObject.Query.FilterBean> matches) {
        if (ObjectUtil.isEmpty(matches)) {
            return StringUtil.EMPTY;
        }
        return matches.stream().map(m -> {
            String fieldKey = m.getFieldKey();
            String type = m.getType();
            List<String> fieldValues = m.getFieldValues();
            return fieldValues.stream().filter(v -> ObjectUtil.isNotEmpty(v)).map(v -> {
                return ComDSLParse.parseRange(ComDSLParse.encode(v), type);
            }).collect(joining(" OR ".concat(fieldKey), fieldKey, " "));

        }).collect(joining(" ) AND ( ", " AND( ", ")"));
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
