package com.mesalab.recommendation.valid;

import com.mesalab.common.enums.*;
import com.mesalab.qgw.service.MetadataService;
import com.mesalab.common.entity.DSLObject;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.recommendation.enums.LogicTypeEnum;
import com.mesalab.recommendation.enums.SortTypeEnum;
import com.mesalab.recommendation.exception.RecommendationErrorMessage;
import com.zdjizhi.utils.StringUtil;
import org.apache.commons.lang3.EnumUtils;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.regex.Pattern;

import static com.mesalab.common.enums.EngineTypeEnum.ANALYSIS_ENGINE;

/**
 * @Date: 2020-07-20 18:10
 * @Author : wangwei
 * @ClassName : DSLValidate
 * @Description : DSL格式校验
 */

@Component
public class DslValidate {

    public static final Pattern strFormatDateTime = Pattern.compile("\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}", Pattern.CASE_INSENSITIVE);

    @Autowired
    private MetadataService metadataService;

    public void executeValidate(DSLObject dslObject) throws BusinessException {
        if (StringUtil.isEmpty(dslObject)) {
            throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), RecommendationErrorMessage.DSL_OBJECT_IS_INVALID));
        }
        DSLObject.Query query = dslObject.getQuery();
        if (StringUtil.isEmpty(query)) {
            throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),RecommendationErrorMessage.QUERY_IS_INVALID));
        }
        if (StringUtil.isEmpty(EngineTypeEnum.getByEngine(query.getDataEngine()))) {
            throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),RecommendationErrorMessage.DATA_ENGINE_IS_INVALID));
        }
        if (ANALYSIS_ENGINE.getEngine().equals(query.getDataEngine())) {
            if (StringUtil.isBlank(query.getDataSource())) {
                throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                        String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),RecommendationErrorMessage.DATASOURCE_IS_INVALID));
            }
        } else {
            if (StringUtil.isBlank(metadataService.getDBTypeByTableName(query.getDataSource()))) {
                throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                        String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),RecommendationErrorMessage.DATASOURCE_IS_INVALID));
            }
        }
        DSLObject.Query.Parameters parameters = query.getParameters();
        if (StringUtil.isEmpty(parameters)) {
            throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),RecommendationErrorMessage.PARAMETERS_IS_INVALID));
        }
        if (!isValidMatch(parameters.getMatch())) {
            throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),RecommendationErrorMessage.MATCH_IS_INVALID));
        }
        if (!isValidRange(parameters.getRange())) {
            throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),RecommendationErrorMessage.RANGE_TYPE_IS_INVALID));
        }
        if (!isValidDistinct(parameters.getDistinct())) {
            throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),RecommendationErrorMessage.DISTINCT_TYPE_IS_INVALID));
        }
        if (!isValidIntervals(parameters.getIntervals())) {
            throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),RecommendationErrorMessage.INTERVALS_TYPE_IS_INVALID));
        }
        if (!isValidSort(parameters.getSort())) {
            throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),RecommendationErrorMessage.SORT_TYPE_IS_INVALID));
        }
    }

    /**
     * 校验sort:
     * 1.是否属于{@link RangeTypeEnum}限定类型
     *
     * @param sort
     * @return
     */
    private boolean isValidSort(List<DSLObject.Query.Parameters.Sort> sort) {
        if (CollectionUtils.isEmpty(sort)) {
            return true;
        }
        sort.forEach(o -> {
            if (!StringUtil.isBlank(o.getType())) {
                if (StringUtil.isEmpty(SortTypeEnum.getByType(StringUtil.lowerCase(o.getType())))) {
                    throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                            String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),RecommendationErrorMessage.SORT_TYPE_IS_INVALID));
                }
            }
        });
        return true;
    }

    /**
     * 校验intervals
     *
     * @param intervals
     * @return
     */
    private boolean isValidIntervals(List<String> intervals) {
        if (CollectionUtils.isEmpty(intervals)) {
            return true;
        }
        if (intervals.size() != 1) {
            return false;
        }
        String[] split = intervals.get(0).split("/");
        if (split.length != 2) {
            return false;
        }
        for (String dateTimeStr : split) {
            if (!strFormatDateTime.matcher(dateTimeStr).find()) {
                throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                        String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),RecommendationErrorMessage.TIME_FORMAT_ERROR));
            }
        }
        try {
            DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
            if (dateTimeFormatter.parseMillis(split[1]) < dateTimeFormatter.parseMillis(split[0])) {
                throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                        String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),RecommendationErrorMessage.INTERVALS_VALUE_ERROR));
            }
        } catch (RuntimeException e) {
            throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),RecommendationErrorMessage.TIME_FORMAT_ERROR));
        }
        return true;
    }


    /**
     * 校验Distinct:
     * 1.是否属于{@link RangeTypeEnum}限定类型
     *
     * @param distinct
     * @return
     */
    private boolean isValidDistinct(DSLObject.Query.Parameters.Distinct distinct) {
        if (StringUtil.isEmpty(distinct)) {
            return true;
        }
        validLogicType(distinct.getLogicType());
        distinct.getCondition().forEach(o -> {
            if (StringUtil.isNotBlank(o.getType())) {
                if (!EnumUtils.isValidEnum(RangeTypeEnum.class, StringUtil.upperCase(o.getType()))){
                    throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                            String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),RecommendationErrorMessage.DISTINCT_TYPE_ERROR));
                }
            }
        });
        return true;
    }

    /**
     * 校验range:
     * 1.是否属于{@link RangeTypeEnum}限定类型
     *
     * @param range
     * @return
     */
    private boolean isValidRange(DSLObject.Query.Parameters.Range range) {
        if (StringUtil.isEmpty(range)) {
            return true;
        }
        validLogicType(range.getLogicType());
        range.getCondition().forEach(o -> {
            if (StringUtil.isNotBlank(o.getType())) {
                if (!EnumUtils.isValidEnum(RangeTypeEnum.class, StringUtil.upperCase(o.getType()))){
                    throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                            String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),RecommendationErrorMessage.RANGE_TYPE_ERROR));
                }
            }
        });
        return true;
    }

    /**
     * 校验match:
     * 1.是否属于{@link MatchTypeEnum}限定类型
     *
     * @param match
     * @return
     */
    private boolean isValidMatch(DSLObject.Query.Parameters.Match match) {
        if (StringUtil.isEmpty(match)) {
            return true;
        }
        String logicType = match.getLogicType();
        validLogicType(logicType);
        match.getCondition().forEach(o -> {
            if (StringUtil.isNotBlank(o.getType())) {
                if (!EnumUtils.isValidEnum(MatchTypeEnum.class, StringUtil.upperCase(o.getType()))){
                    throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                            String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),RecommendationErrorMessage.MATCH_TYPE_ERROR));
                }
            }
        });
        return true;
    }

    private void validLogicType(String logicType) {
        if (StringUtil.isNotBlank(logicType)) {
            if (StringUtil.isEmpty(LogicTypeEnum.getByType(StringUtil.lowerCase(logicType)))) {
                throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                        String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),RecommendationErrorMessage.LOGIC_TYPE_IS_INVALID));
            }
        }
    }
}