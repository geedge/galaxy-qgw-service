package com.mesalab.services.common.dsl;

import cn.hutool.core.date.DateException;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjectUtil;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.mesalab.cn.entity.pojo.DSLObject;
import com.mesalab.cn.entity.pojo.DSLParser;
import com.mesalab.cn.enums.RangeTypeEnum;
import com.mesalab.common.enums.HttpStatusCodeEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.geedgenetworks.utils.StringUtil;
import com.mesalab.common.exception.CommonErrorCode;

import java.util.List;

import static java.util.stream.Collectors.joining;

public class ComDSLParse extends DSLParser {
    public static String parseInterval(ComDSLObject.Query query, String defField, boolean format) {
        try {
            if (ObjectUtil.isEmpty(query) || ObjectUtil.isEmpty(query.getParameters()) || ObjectUtil.isEmpty(query.getParameters().getIntervals())) {
                return StringUtil.EMPTY;
            }
            List<String> intervals = Splitter.on("/").omitEmptyStrings().splitToList(query.getParameters().getIntervals().get(0));
            if (format) {
                long startTime = DateUtil.parse(intervals.get(0)).toTimestamp().getTime() / 1000;
                long endTime = DateUtil.parse(intervals.get(1)).toTimestamp().getTime() / 1000;
                return Joiner.on(" ").join(" AND ", defField, ">=", startTime, " AND ", defField, "<", endTime);
            }
            return Joiner.on(" ").join(" AND ", defField, ">=", intervals.get(0), " AND ", defField, "<", intervals.get(1));
        } catch (DateException e) {
            throw new BusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.TIME_FORMAT_ERROR));
        }
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
                return ComDSLParse.parseRange(!NumberUtil.isNumber(v) ? "'".concat(ComDSLParse.encode(v)).concat("'") : v, type);
            }).collect(joining((RangeTypeEnum.NE.getType().equalsIgnoreCase(type) ? " AND " : " OR ").concat(fieldKey), fieldKey, " "));
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
