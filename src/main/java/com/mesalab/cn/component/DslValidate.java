package com.mesalab.cn.component;

import com.mesalab.cn.common.enums.*;
import com.mesalab.cn.common.pojo.DSLObject;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.qgw.service.MetadataService;
import com.zdjizhi.utils.StringUtil;
import org.apache.commons.lang.Validate;
import org.apache.commons.lang3.EnumUtils;
import org.apache.http.HttpStatus;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @Date: 2020-07-20 18:10
 * @Author : wangwei
 * @ClassName : DSLValidate
 * @Description : DSL格式校验
 */

@Component
public class DslValidate {

    public static final Pattern strFormatDateTime = Pattern.compile("\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}", Pattern.CASE_INSENSITIVE);

    @Resource(name = "nacosMetadataService")
    private MetadataService metadataService;

    public void executeValidate(DSLObject dslObject) throws BusinessException {
        if (StringUtil.isEmpty(dslObject)) {
            throw new BusinessException(HttpStatus.SC_BAD_REQUEST, ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                    "DSLObject is invalid");
        }
        DSLObject.Query query = dslObject.getQuery();
        if (StringUtil.isEmpty(query)) {
            throw new BusinessException(HttpStatus.SC_BAD_REQUEST, ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                    "DSLObject.query is invalid");
        }
        if (StringUtil.isEmpty(EngineTypeEnum.getByEngine(query.getDataEngine()))) {
            throw new BusinessException(HttpStatus.SC_BAD_REQUEST, ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                    "DSLObject.query.dataEngine is invalid");
        }
        if (StringUtil.isBlank(metadataService.getDBTypeByTableName(query.getDataSource()))) {
            throw new BusinessException(HttpStatus.SC_BAD_REQUEST, ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                    "DSLObject.query.dataSource is invalid");
        }
        DSLObject.Query.Parameters parameters = query.getParameters();
        if (StringUtil.isEmpty(parameters)) {
            throw new BusinessException(HttpStatus.SC_BAD_REQUEST, ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                    "DSLObject.query.parameters is invalid");
        }
        if (!isValidMatch(parameters.getMatch())) {
            throw new BusinessException(HttpStatus.SC_BAD_REQUEST, ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                    "DSLObject.query.Match type is invalid");
        }
        if (!isValidRange(parameters.getRange())) {
            throw new BusinessException(HttpStatus.SC_BAD_REQUEST, ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                    "DSLObject.query.Range type is invalid");
        }
        if (!isValidDistinct(parameters.getDistinct())) {
            throw new BusinessException(HttpStatus.SC_BAD_REQUEST, ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                    "DSLObject.query.Distinct type is invalid");
        }
        if (!isValidIntervals(parameters.getIntervals())) {
            throw new BusinessException(HttpStatus.SC_BAD_REQUEST, ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                    "DSLObject.query.Intervals is invalid");
        }
        if (!isValidSort(parameters.getSort())) {
            throw new BusinessException(HttpStatus.SC_BAD_REQUEST, ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                    "DSLObject.query.Sort type is invalid");
        }
     }

    /**
     * 校验sort:
     * 1.是否属于{@link com.mesalab.cn.common.enums.RangeTypeEnum}限定类型
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
                    throw new BusinessException(HttpStatus.SC_BAD_REQUEST, ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                            "Sort type is invalid");
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


    /**
     * 校验Distinct:
     * 1.是否属于{@link com.mesalab.cn.common.enums.RangeTypeEnum}限定类型
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
                Validate.isTrue(EnumUtils.isValidEnum(RangeTypeEnum.class, StringUtil.upperCase(o.getType())), "distinct type is illegal");
            }
        });
        return true;
    }

    /**
     * 校验range:
     * 1.是否属于{@link com.mesalab.cn.common.enums.RangeTypeEnum}限定类型
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
                Validate.isTrue(EnumUtils.isValidEnum(RangeTypeEnum.class, StringUtil.upperCase(o.getType())), "range type is illegal");
            }
        });
        return true;
    }

    /**
     * 校验match:
     * 1.是否属于{@link com.mesalab.cn.common.enums.MatchTypeEnum}限定类型
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
                Validate.isTrue(EnumUtils.isValidEnum(MatchTypeEnum.class, StringUtil.upperCase(o.getType())), "match type is illegal");
            }
        });
        return true;
    }

    private void validLogicType(String logicType) {
        if (StringUtil.isNotBlank(logicType)) {
            if (StringUtil.isEmpty(LogicTypeEnum.getByType(StringUtil.lowerCase(logicType)))) {
                throw new BusinessException(HttpStatus.SC_BAD_REQUEST, ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                        "logicType is invalid");
            }
        }
    }
}