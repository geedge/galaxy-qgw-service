package com.mesalab.knowledge.service;

import cn.hutool.core.date.DateException;
import cn.hutool.core.date.DateUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ProcessingMessage;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.google.common.base.Splitter;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.knowledge.entity.*;
import com.mesalab.knowledge.enums.DataSourceEnum;
import com.mesalab.knowledge.enums.MatchEnum;
import com.mesalab.knowledge.enums.RangeEnum;
import com.mesalab.knowledge.exception.KnowLedgeErrorMessage;
import com.mesalab.knowledge.strategy.QueryProvider;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.zdjizhi.utils.JsonMapper;
import com.zdjizhi.utils.StringUtil;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import java.io.IOException;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static java.util.Objects.*;

/**
 * @description: for IP learning
 * @author: zhq
 * @create: 2020-07-06
 **/
@Service
public class KnowledgeService {

    private static final Log log = LogFactory.get();
    @Value("${arango.maxrows}")
    private String maxrows;//查询最大条数

    @Autowired
    Map<String, QueryProvider> provider = new ConcurrentHashMap<>();


    public BaseResult query(Object object) {
        //1.结构校验
        validateJson(JsonMapper.toJsonString(object));
        DSLObject dSLObject = (DSLObject) JsonMapper.fromJsonString(JsonMapper.toJsonString(object), DSLObject.class);
        validate(dSLObject);
        DSLObject.DSLQuery dslQeruy = dSLObject.getQuery();
        //2.查询
        Object result = provider.get(dslQeruy.getDataSource()).query(dslQeruy);
        return BaseResultGenerator.success(result);

    }

    /**
     * @return void
     * @Author zhq
     * @Description //TODO 参数校验
     * @Date 2020\7\21 0021
     * @Param [dslQuery]
     **/
    private void validate(DSLObject dSLObject) {
        DSLObject.DSLQuery dslQuery = dSLObject.getQuery();

        if (ObjectUtils.isEmpty(dslQuery)){
            throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), KnowLedgeErrorMessage.QUERY_IS_INVALID));
        }
        Parameters parameters = dslQuery.getParameters();
        if (ObjectUtils.isEmpty(dslQuery.getDataSource())){
            throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), KnowLedgeErrorMessage.DATASOURCE_IS_INVALID));
        }
        if (ObjectUtils.isEmpty(EnumUtils.getEnum(DataSourceEnum.class,StringUtil.upperCase(dslQuery.getDataSource())))){
            throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), KnowLedgeErrorMessage.DATASOURCE_IS_ERROR));
        }

        if (isNull(parameters)) {
            return;
        }

        List<Match> matchs = parameters.getMatch();
        List<Range> ranges = parameters.getRange();
        List<String> intervals = parameters.getIntervals();
        List<Sort> sorts = parameters.getSort();
        String limit = parameters.getLimit();
        if (!ObjectUtils.isEmpty(matchs)) {
            matchs.forEach(m -> {
                if (!EnumUtils.isValidEnum(MatchEnum.class, StringUtil.upperCase(m.getType()))){
                    throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                            String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), KnowLedgeErrorMessage.MATCH_TYPE_ERROR));
                }
                //正则匹配方式 禁止 $xxx* 形式的value
                if (MatchEnum.REGEX.getType().equals(m.getType())) {
                    m.getFieldValues().forEach(mv -> {
                        if((String.valueOf(mv).startsWith("$") && String.valueOf(mv).endsWith("*"))){
                            throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), KnowLedgeErrorMessage.MATCHING_SIGN));
                        }
                    });
                }
            });
        }
        if (!ObjectUtils.isEmpty(ranges)) {
            ranges.forEach(r -> {
                if (!EnumUtils.isValidEnum(RangeEnum.class, StringUtil.upperCase(r.getType()))){
                    throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                            String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), KnowLedgeErrorMessage.RANGE_TYPE_ERROR));
                }
            });
        }
        if (!ObjectUtils.isEmpty(sorts)) {
            sorts.forEach(s -> {
                if (!("asc".equalsIgnoreCase(s.getType()) || "desc".equalsIgnoreCase(s.getType()))){
                    throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                            String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), KnowLedgeErrorMessage.SORT_TYPE_ERROR));
                }
            });
        }
        if (!ObjectUtils.isEmpty(intervals)) {
            try {
                List<String> times = Splitter.on("/").splitToList(intervals.get(0));
                if (ObjectUtils.isEmpty(times) || times.size() != 2) {
                    throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                            String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), KnowLedgeErrorMessage.INTERVALS_IS_INVALID));
                }
                for (String dateTimeStr : times) {
                    DateUtil.parse(dateTimeStr);
                }
            } catch (DateException e) {
                throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                        String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), QGWMessageConst.TIME_FORMAT_ERROR));
            }
        }
        if (nonNull(limit)) {
            List<String> limits = Splitter.on(",").trimResults().omitEmptyStrings().splitToList(limit);
            if (ObjectUtils.isEmpty(limits) || (limit.contains(",") && limits.size() != 2)){
                throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                        String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), KnowLedgeErrorMessage.LIMIT_IS_ILLEGAL));
            }

            for (String l : limits) {
                if (!(l.length() <= maxrows.length() && Integer.valueOf(maxrows) >= Integer.valueOf(l) && Integer.valueOf(l) > 0)){
                    throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                            String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), String.format(KnowLedgeErrorMessage.LIMIT_IS_ILLEGAL,maxrows)));
                }
            }
        }
    }

    private void validateJson(String jsonString) {
        JsonSchemaFactory factory = JsonSchemaFactory.byDefault();
        //通过jsonschemaFactory获取jsonnode对象
        try {

            JsonNode schemaNode = JsonLoader.fromResource("/dsl-validation.json");
            //通过jsonstr字符串获取对应的jsonnode对象
            JsonNode dataNode = JsonLoader.fromString(jsonString);
            JsonSchema jsonSchema = factory.getJsonSchema(schemaNode);
            //使用json-schema-validator中的jsonschema对象的validate方法对数据进行校验
            //获取处理的报告信息
            ProcessingReport processingReport = jsonSchema.validate(dataNode);
            //获取完整的报告信息
            Iterator<ProcessingMessage> iterator = processingReport.iterator();
            StringBuffer sb = new StringBuffer();
            while (iterator.hasNext()) {
                ProcessingMessage next = iterator.next();
                JsonNode jsonNode = next.asJson();
                sb.append("pointer on ");
                sb.append(jsonNode.get("instance").get("pointer"));
                sb.append(", ");
                sb.append(next.getMessage());
                sb.append(". ");
            }
            //判断校验是否成功，如果为true成功
            Validate.isTrue(processingReport.isSuccess(), sb.toString());
        } catch (ProcessingException e) {
            log.error(e);
        } catch (IOException e) {
            log.error(e);
        }
    }

}
