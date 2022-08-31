package com.mesalab.network.dsl;

import com.mesalab.cn.common.constant.ErrorMessage;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.knowledge.enums.MatchEnum;
import com.mesalab.knowledge.enums.RangeEnum;
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
 * @Date: 2020-09-17 18:10
 * @Author : wangwei
 * @ClassName : DSLValidate
 * @Description : DSL格式校验
 */
@Component
public class DSLValidate {

    public static final Pattern periodOfPT = Pattern.compile("PT(\\d+)[SMH]", Pattern.CASE_INSENSITIVE);
    public static final Pattern periodOfP = Pattern.compile("P(\\d+)[DWMY]", Pattern.CASE_INSENSITIVE);
    public static final Pattern strFormatDateTime = Pattern.compile("\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}", Pattern.CASE_INSENSITIVE);


    public void executeValidate(DSLObject dslObject) throws BusinessException {
        if (StringUtil.isEmpty(dslObject)) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.ILLEGAL_ARGUMENT_EXCEPTION.getCode(),
                    ErrorMessage.DSL_OBJECT_IS_INVALID);
        }
        if (StringUtil.isEmpty(dslObject.getQuery())) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.ILLEGAL_ARGUMENT_EXCEPTION.getCode(),
                    ErrorMessage.QUERY_IS_INVALID);
        }
        DSLObject.QueryBean.Parameters parameters = dslObject.getQuery().getParameters();
        if (StringUtil.isEmpty(parameters)) {
            return;
        }
        if (!isValidGranularity(parameters.getGranularity())) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.ILLEGAL_ARGUMENT_EXCEPTION.getCode(),
                    ErrorMessage.GRANULARITY_VALUE_IS_INVALID);
        }
        if (!isValidMatch(parameters.getMatch())) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.ILLEGAL_ARGUMENT_EXCEPTION.getCode(),
                    ErrorMessage.MATCH_IS_INVALID);
        }
        if (!isValidRange(parameters.getRange())) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.ILLEGAL_ARGUMENT_EXCEPTION.getCode(),
                    ErrorMessage.RANGE_TYPE_IS_INVALID);
        }
        if (!isValidIntervals(parameters.getIntervals())) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.ILLEGAL_ARGUMENT_EXCEPTION.getCode(),
                    ErrorMessage.INTERVALS_TYPE_IS_INVALID);
        }
    }

    /**
     * 时间粒度校验, 遵循ISO8601 durations 定义
     *
     * @param granularity
     * @return
     */
    private boolean isValidGranularity(String granularity) {
        if (StringUtil.isBlank(granularity)) {
            return true;
        }
        if (periodOfP.matcher(granularity).find() || periodOfPT.matcher(granularity).find()) {
            return true;
        }
        return false;
    }

    /**
     * 校验match:
     * 1.是否属于{@link MatchEnum}限定类型
     * 2.不能以*开始、或$结尾
     *
     * @param filterBeanList
     * @return
     */
    private boolean isValidMatch(List<DSLObject.QueryBean.Parameters.FilterBean> filterBeanList) {
        if (CollectionUtils.isEmpty(filterBeanList)) {
            return true;
        }
        for (DSLObject.QueryBean.Parameters.FilterBean match : filterBeanList) {
            Validate.isTrue(EnumUtils.isValidEnum(MatchEnum.class, StringUtil.upperCase(match.getType())), ErrorMessage.MATCH_FIELD_VALUES_ERROR);
            for (String fieldValue : match.getFieldValues()) {
                if (fieldValue.startsWith("*") || fieldValue.endsWith("$")) {
                    throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.ILLEGAL_ARGUMENT_EXCEPTION.getCode(),
                            ErrorMessage.MATCH_FIELD_VALUES_ERROR);
                }
            }
        }
        return true;
    }

    /**
     * 校验range:
     * 1.是否属于{@link RangeEnum}限定类型
     *
     * @param filterBeanList
     * @return
     */
    private boolean isValidRange(List<DSLObject.QueryBean.Parameters.FilterBean> filterBeanList) {
        if (CollectionUtils.isEmpty(filterBeanList)) {
            return true;
        }
        for (DSLObject.QueryBean.Parameters.FilterBean range : filterBeanList) {
            Validate.isTrue(EnumUtils.isValidEnum(RangeEnum.class, StringUtil.upperCase(range.getType())), "range type is illegal");
        }
        return true;
    }

    /**
     * 校验Interval:
     * 1.目前只支持between类型: ["2020-01-01 00:00:00/2020-01-02 00:00:00"]
     * 2.时间区间必须是 开始时间 < 结束时间
     *
     * @param intervals
     */
    private boolean isValidIntervals(List<String> intervals) {
        if (CollectionUtils.isEmpty(intervals)) {
            return true;
        }
        if (intervals.size() != 1) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.ILLEGAL_ARGUMENT_EXCEPTION.getCode(),
                    ErrorMessage.INTERVALS_PARAM_ERROR);
        }
        String[] split = intervals.get(0).split("/");
        if (split.length != 2) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.ILLEGAL_ARGUMENT_EXCEPTION.getCode(),
                    ErrorMessage.INTERVALS_PARAM_ERROR);
        }
        for (String dateTimeStr : split) {
            if (!strFormatDateTime.matcher(dateTimeStr).find()) {
                throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.ILLEGAL_ARGUMENT_EXCEPTION.getCode(),
                        ErrorMessage.TIME_FORMAT_ERROR);
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
