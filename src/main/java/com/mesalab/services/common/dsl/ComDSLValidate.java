package com.mesalab.services.common.dsl;

import com.mesalab.cn.common.constant.ErrorMessage;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.knowledge.enums.MatchEnum;
import com.mesalab.knowledge.enums.RangeEnum;
import com.mesalab.knowledge.enums.SortEnum;
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

    public void validation(ComDSLObject dslObject) throws BusinessException {
        if (StringUtil.isEmpty(dslObject)) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.ILLEGAL_ARGUMENT_EXCEPTION.getCode(),
                    ErrorMessage.DSL_OBJECT_IS_INVALID);
        }
        ComDSLObject.Query query;
        if (StringUtil.isEmpty(query = dslObject.getQuery())) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.ILLEGAL_ARGUMENT_EXCEPTION.getCode(),
                    ErrorMessage.QUERY_IS_INVALID);
        }
        if (StringUtil.isEmpty(query.getDataEngine())) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.ILLEGAL_ARGUMENT_EXCEPTION.getCode(),
                    ErrorMessage.DATA_ENGINE_IS_INVALID);
        }
        if (StringUtil.isEmpty(query.getDataSource())) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.ILLEGAL_ARGUMENT_EXCEPTION.getCode(),
                    ErrorMessage.DATASOURCE_IS_INVALID);
        }
        ComDSLObject.Query.QueryBean parameters;
        if (StringUtil.isEmpty(parameters = query.getParameters())) {
            return;
        }
        if (!isValidGranularity(parameters.getGranularity())) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.ILLEGAL_ARGUMENT_EXCEPTION.getCode(),
                    ErrorMessage.GRANULARITY_VALUE_IS_INVALID);
        }
        if (!isValidMatch(parameters.getMatch())) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.ILLEGAL_ARGUMENT_EXCEPTION.getCode(),
                    ErrorMessage.MATCH_TYPE);
        }
        if (!isValidRange(parameters.getRange())) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.ILLEGAL_ARGUMENT_EXCEPTION.getCode(),
                    ErrorMessage.INTERVALS_PARAM_ERROR);
        }
        if (!isValidIntervals(parameters.getIntervals())) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.ILLEGAL_ARGUMENT_EXCEPTION.getCode(),
                    ErrorMessage.INTERVALS_PARAM_ERROR);
        }
        if (!isValidSort(parameters.getSort())) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.ILLEGAL_ARGUMENT_EXCEPTION.getCode(),
                    ErrorMessage.INTERVALS_PARAM_ERROR);
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
            Validate.isTrue(EnumUtils.isValidEnum(SortEnum.class, StringUtil.upperCase(sortBean.getType())), ErrorMessage.SORT_TYPE_IS_INVALID);
            String fieldKey = sortBean.getFieldKey();
            if (StringUtil.isBlank(fieldKey)) {
                throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.ILLEGAL_ARGUMENT_EXCEPTION.getCode(), ErrorMessage.SORT_FIELD_VALUES);
            }
        }
        return true;
    }

    private boolean isValidRange(List<ComDSLObject.Query.FilterBean> rangeList) {
        if (CollectionUtils.isEmpty(rangeList)) {
            return true;
        }
        for (ComDSLObject.Query.FilterBean rangeBean : rangeList) {
            Validate.isTrue(EnumUtils.isValidEnum(RangeEnum.class, StringUtil.upperCase(rangeBean.getType())), ErrorMessage.RANGE_TYPE_IS_INVALID);
            String fieldKey = rangeBean.getFieldKey();
            if (StringUtil.isBlank(fieldKey)) {
                throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.ILLEGAL_ARGUMENT_EXCEPTION.getCode(), ErrorMessage.RANGE_FIELD_VALUES);
            }
        }
        return true;
    }

    private boolean isValidMatch(List<ComDSLObject.Query.FilterBean> matchList) {
        if (CollectionUtils.isEmpty(matchList)) {
            return true;
        }
        for (ComDSLObject.Query.FilterBean matchBean : matchList) {
            Validate.isTrue(EnumUtils.isValidEnum(MatchEnum.class, StringUtil.upperCase(matchBean.getType())), ErrorMessage.MATCH_TYPE);

            if (MatchEnum.REGEX.getType().equals(matchBean.getType())) {
                matchBean.getFieldValues().forEach(mv -> {
                    org.apache.commons.lang3.Validate.isTrue(!(String.valueOf(mv).startsWith("$") && String.valueOf(mv).endsWith("*")), ErrorMessage.MATCHING_SIGN);
                });
            }

            /*for (String fieldValue : matchBean.getFieldValues()) {
                if (fieldValue.startsWith("*") || fieldValue.endsWith("$")) {
                    throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(), "Match fieldValues cannot startWith '*' or endWith '$'");
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
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.ILLEGAL_ARGUMENT_EXCEPTION.getCode(), ErrorMessage.INTERVALS_PARAM_ERROR);
        }
        String[] split = intervalsBean.get(0).split("/");
        if (split.length != 2) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.ILLEGAL_ARGUMENT_EXCEPTION.getCode(), ErrorMessage.INTERVALS_PARAM_ERROR);
        }
        for (String dateTimeStr : split) {
            if (!strFormatDateTime.matcher(dateTimeStr).find()) {
                throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.ILLEGAL_ARGUMENT_EXCEPTION.getCode(), ErrorMessage.TIME_FORMAT_ERROR);
            }
        }
        try {
            DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
            if (dateTimeFormatter.parseMillis(split[1]) < dateTimeFormatter.parseMillis(split[0])) {
                throw new RuntimeException("Intervals value should be [start, end]");
            }
        } catch (RuntimeException e) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.ILLEGAL_ARGUMENT_EXCEPTION.getCode(), e.getMessage());
        }
        return true;
    }
}
