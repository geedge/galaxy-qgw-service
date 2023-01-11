package com.mesalab.recommendation.valid;

import com.mesalab.common.entity.ComDSLObject;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.common.enums.MatchEnum;
import com.mesalab.common.enums.RangeEnum;
import com.mesalab.common.enums.SortEnum;
import com.zdjizhi.utils.StringUtil;
import org.apache.commons.lang.Validate;
import org.apache.commons.lang3.EnumUtils;
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

    public void validation(ComDSLObject dslObject) throws QGWBusinessException {
        if (StringUtil.isEmpty(dslObject)) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), QGWMessageConst.DSL_OBJECT_IS_INVALID));
        }
        ComDSLObject.Query query;
        if (StringUtil.isEmpty(query = dslObject.getQuery())) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),QGWMessageConst.QUERY_IS_INVALID));
        }
        if (StringUtil.isEmpty(query.getDataEngine())) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),QGWMessageConst.DATA_ENGINE_IS_INVALID));
        }
        if (StringUtil.isEmpty(query.getDataSource())) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),QGWMessageConst.DATASOURCE_IS_INVALID));
        }
        ComDSLObject.Query.QueryBean parameters;
        if (StringUtil.isEmpty(parameters = query.getParameters())) {
            return;
        }
        if (!isValidGranularity(parameters.getGranularity())) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),QGWMessageConst.GRANULARITY_VALUE_IS_INVALID));
        }
        if (!isValidMatch(parameters.getMatch())) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),QGWMessageConst.MATCH_TYPE));
        }
        if (!isValidRange(parameters.getRange())) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),QGWMessageConst.INTERVALS_PARAM_ERROR));
        }
        if (!isValidIntervals(parameters.getIntervals())) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),QGWMessageConst.INTERVALS_PARAM_ERROR));
        }
        if (!isValidSort(parameters.getSort())) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),QGWMessageConst.INTERVALS_PARAM_ERROR));
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
            Validate.isTrue(EnumUtils.isValidEnum(SortEnum.class, StringUtil.upperCase(sortBean.getType())), QGWMessageConst.SORT_TYPE_IS_INVALID);
            String fieldKey = sortBean.getFieldKey();
            if (StringUtil.isBlank(fieldKey)) {
                throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                        String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),QGWMessageConst.SORT_FIELD_VALUES));
            }
        }
        return true;
    }

    private boolean isValidRange(List<ComDSLObject.Query.FilterBean> rangeList) {
        if (CollectionUtils.isEmpty(rangeList)) {
            return true;
        }
        for (ComDSLObject.Query.FilterBean rangeBean : rangeList) {

            if (!EnumUtils.isValidEnum(RangeEnum.class, StringUtil.upperCase(rangeBean.getType()))){
                throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                        String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),QGWMessageConst.RANGE_TYPE_IS_INVALID));

            }
            String fieldKey = rangeBean.getFieldKey();
            if (StringUtil.isBlank(fieldKey)) {
                throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                        String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),QGWMessageConst.RANGE_FIELD_VALUES));
            }
        }
        return true;
    }

    private boolean isValidMatch(List<ComDSLObject.Query.FilterBean> matchList) {
        if (CollectionUtils.isEmpty(matchList)) {
            return true;
        }
        for (ComDSLObject.Query.FilterBean matchBean : matchList) {
            if (!EnumUtils.isValidEnum(MatchEnum.class, StringUtil.upperCase(matchBean.getType()))){
                throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                        String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),QGWMessageConst.MATCH_TYPE));
            }

            if (MatchEnum.REGEX.getType().equals(matchBean.getType())) {
                matchBean.getFieldValues().forEach(mv -> {
                    if (String.valueOf(mv).startsWith("$") && String.valueOf(mv).endsWith("*")){
                        throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                                String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),QGWMessageConst.MATCHING_SIGN));
                    }
                });
            }

            /*for (String fieldValue : matchBean.getFieldValues()) {
                if (fieldValue.startsWith("*") || fieldValue.endsWith("$")) {
                    throw new QGWBusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(), "Match fieldValues cannot startWith '*' or endWith '$'");
                }
            }*/
        }
        return true;
    }

    private boolean isValidIntervals(List<String> intervalsBean) {
        if (CollectionUtils.isEmpty(intervalsBean)) {
            return true;
        }
        if (intervalsBean.size() != 1) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),QGWMessageConst.INTERVALS_PARAM_ERROR));
        }
        String[] split = intervalsBean.get(0).split("/");
        if (split.length != 2) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),QGWMessageConst.INTERVALS_PARAM_ERROR));
        }
        for (String dateTimeStr : split) {
            if (!strFormatDateTime.matcher(dateTimeStr).find()) {
                throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                        String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),QGWMessageConst.TIME_FORMAT_ERROR));
            }
        }
        try {
            DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
            if (dateTimeFormatter.parseMillis(split[1]) < dateTimeFormatter.parseMillis(split[0])) {
                throw new RuntimeException("Intervals value should be [start, end]");
            }
        } catch (RuntimeException e) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),e.getMessage()));
        }
        return true;
    }
}
