package com.mesalab.services.common.dsl;

import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.knowledge.enums.MatchEnum;
import com.mesalab.knowledge.enums.RangeEnum;
import com.mesalab.knowledge.enums.SortEnum;
import com.zdjizhi.utils.StringUtil;
import org.apache.commons.lang.Validate;
import org.apache.commons.lang3.EnumUtils;
import org.apache.http.HttpStatus;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.regex.Pattern;

/**
 * @author tiangaochao
 */
@Component
public class ComDSLValidate {

    public static final Pattern periodOfPT = Pattern.compile("PT(\\d+)[SMH]", Pattern.CASE_INSENSITIVE);
    public static final Pattern periodOfP = Pattern.compile("P(\\d+)[DWMY]", Pattern.CASE_INSENSITIVE);
    public static final Pattern strFormatDateTime = Pattern.compile("\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}", Pattern.CASE_INSENSITIVE);

    public void validation(ComDSLObject dslObject) throws BusinessException {
        if (StringUtil.isEmpty(dslObject)) {
            throw new BusinessException(HttpStatus.SC_BAD_REQUEST, ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                    "DSLObject is invalid");
        }
        ComDSLObject.Query query;
        if (StringUtil.isEmpty(query = dslObject.getQuery())) {
            throw new BusinessException(HttpStatus.SC_BAD_REQUEST, ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                    "DSLObject.query is invalid");
        }
        if (StringUtil.isEmpty(query.getDataEngine())) {
            throw new BusinessException(HttpStatus.SC_BAD_REQUEST, ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                    "DSLObject.query.dataEngine is invalid");
        }
        if (StringUtil.isEmpty(query.getDataSource())) {
            throw new BusinessException(HttpStatus.SC_BAD_REQUEST, ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                    "DSLObject.query.dataSource is invalid");
        }
        ComDSLObject.Query.QueryBean parameters;
        if (StringUtil.isEmpty(parameters = query.getParameters())) {
            return;
        }
        if (!isValidGranularity(parameters.getGranularity())) {
            throw new BusinessException(HttpStatus.SC_BAD_REQUEST, ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                    "query.Granularity value is invalid");
        }
        if (!isValidMatch(parameters.getMatch())) {
            throw new BusinessException(HttpStatus.SC_BAD_REQUEST, ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                    "DSLObject.query.query.Match type is invalid");
        }
        if (!isValidRange(parameters.getRange())) {
            throw new BusinessException(HttpStatus.SC_BAD_REQUEST, ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                    "DSLObject.query.query.Intervals is invalid");
        }
        if (!isValidIntervals(parameters.getIntervals())) {
            throw new BusinessException(HttpStatus.SC_BAD_REQUEST, ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                    "DSLObject.query.query.Intervals is invalid");
        }
        if (!isValidSort(parameters.getSort())) {
            throw new BusinessException(HttpStatus.SC_BAD_REQUEST, ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                    "DSLObject.query.query.Intervals is invalid");
        }
    }

    private boolean isValidGranularity(String granularity) {
        if (StringUtil.isBlank(granularity)) {
            return true;
        }
        if (periodOfP.matcher(granularity).find() || periodOfPT.matcher(granularity).find()) {
            return true;
        }
        return false;
    }

    private boolean isValidSort(List<ComDSLObject.Query.SortBean> sortList) {
        if (CollectionUtils.isEmpty(sortList)) {
            return true;
        }
        for (ComDSLObject.Query.SortBean sortBean : sortList) {
            Validate.isTrue(EnumUtils.isValidEnum(SortEnum.class, StringUtil.upperCase(sortBean.getType())), "sort type is illegal");
            String fieldKey = sortBean.getFieldKey();
            if (StringUtil.isBlank(fieldKey)) {
                throw new BusinessException(HttpStatus.SC_BAD_REQUEST, ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(), "sort fieldValues cannot null");
            }
        }
        return true;
    }

    private boolean isValidRange(List<ComDSLObject.Query.FilterBean> rangeList) {
        if (CollectionUtils.isEmpty(rangeList)) {
            return true;
        }
        for (ComDSLObject.Query.FilterBean rangeBean : rangeList) {
            Validate.isTrue(EnumUtils.isValidEnum(RangeEnum.class, StringUtil.upperCase(rangeBean.getType())), "range type is illegal");
            String fieldKey = rangeBean.getFieldKey();
            if (StringUtil.isBlank(fieldKey)) {
                throw new BusinessException(HttpStatus.SC_BAD_REQUEST, ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(), "range fieldValues cannot null");
            }
        }
        return true;
    }

    private boolean isValidMatch(List<ComDSLObject.Query.FilterBean> matchList) {
        if (CollectionUtils.isEmpty(matchList)) {
            return true;
        }
        for (ComDSLObject.Query.FilterBean matchBean : matchList) {
            Validate.isTrue(EnumUtils.isValidEnum(MatchEnum.class, StringUtil.upperCase(matchBean.getType())), "match type is illegal");
            for (String fieldValue : matchBean.getFieldValues()) {
                if (fieldValue.startsWith("*") || fieldValue.endsWith("$")) {
                    throw new BusinessException(HttpStatus.SC_BAD_REQUEST, ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(), "Match fieldValues cannot startWith '*' or endWith '$'");
                }
            }
        }
        return true;
    }

    private boolean isValidIntervals(List<String> intervalsBean) {
        if (CollectionUtils.isEmpty(intervalsBean)) {
            return true;
        }
        if (intervalsBean.size() != 1) {
            throw new BusinessException(HttpStatus.SC_BAD_REQUEST, ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(), "Intervals param error");
        }
        String[] split = intervalsBean.get(0).split("/");
        if (split.length != 2) {
            throw new BusinessException(HttpStatus.SC_BAD_REQUEST, ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(), "Intervals param error");
        }
        for (String dateTimeStr : split) {
            if (!strFormatDateTime.matcher(dateTimeStr).find()) {
                throw new BusinessException(HttpStatus.SC_BAD_REQUEST, ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(), "Time format should be: yyyy-MM-dd HH:mm:ss");
            }
        }
        try {
            DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
            if (dateTimeFormatter.parseMillis(split[1]) < dateTimeFormatter.parseMillis(split[0])) {
                throw new RuntimeException("Intervals value should be [start, end]");
            }
        } catch (RuntimeException e) {
            throw new BusinessException(HttpStatus.SC_BAD_REQUEST, ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(), e.getMessage());
        }
        return true;
    }
}
