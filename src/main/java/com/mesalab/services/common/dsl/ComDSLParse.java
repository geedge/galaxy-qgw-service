package com.mesalab.services.common.dsl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.mesalab.cn.entity.pojo.DSLObject;
import com.mesalab.cn.entity.pojo.DSLParser;
import com.mesalab.knowledge.enums.MatchEnum;
import com.zdjizhi.utils.DateUtils;
import com.zdjizhi.utils.StringUtil;
import org.joda.time.format.ISODateTimeFormat;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.joining;

public class ComDSLParse extends DSLParser {

    public static final Pattern strFormatDateTime = Pattern.compile("\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}", Pattern.CASE_INSENSITIVE);

    public static String parseInterval(ComDSLObject.Query query, String defField, boolean format) {
        if (ObjectUtil.isEmpty(query) || ObjectUtil.isEmpty(query.getParameters()) || ObjectUtil.isEmpty(query.getParameters().getIntervals())) {
            return StringUtil.EMPTY;
        }
        List<String> intervals = Splitter.on("/").omitEmptyStrings().splitToList(query.getParameters().getIntervals().get(0));
        if (format) {
            long startTime;
            long endTime;
            String start = intervals.get(0);
            String end = intervals.get(1);
            if (strFormatDateTime.matcher(start).find()){
                startTime = DateUtils.convertStringToTimestamp(start, "yyyy-MM-dd HH:mm:ss");
            } else {
                startTime = ISODateTimeFormat.dateTimeNoMillis().parseMillis(start)/1000;
            }
            if (strFormatDateTime.matcher(end).find()){
                endTime = DateUtils.convertStringToTimestamp(end, "yyyy-MM-dd HH:mm:ss");
            } else {
                endTime = ISODateTimeFormat.dateTimeNoMillis().parseMillis(end)/1000;
            }
            return Joiner.on(" ").join(" AND ", defField, ">=", startTime, " AND ", defField, "<", endTime);
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
