package com.mesalab.services.common.dsl;

import cn.hutool.core.util.ObjectUtil;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.mesalab.cn.common.constant.SqlKeywords;
import com.mesalab.cn.common.pojo.DSLObject;
import com.mesalab.cn.common.pojo.DSLParser;
import com.zdjizhi.utils.DateUtils;
import com.zdjizhi.utils.StringUtil;
import org.aspectj.weaver.ast.Instanceof;

import java.util.List;
import java.util.Objects;

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
            return fieldValues.stream().filter(v -> ObjectUtil.isNotEmpty(v)).map(v -> {
                return parseLike(v, m.getType());
            }).collect(joining(" or ".concat(fieldKey).concat(" LIKE "), fieldKey.concat(" LIKE "), " "));
        }).collect(joining(" AND ", " AND ", " "));
    }


    @Override
    public String parseSort(DSLObject dslObject, String sql) {
        return null;
    }

    @Override
    public String parseIntervals(DSLObject dslObject, String sql) {
        return null;
    }
}
