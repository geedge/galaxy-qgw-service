package com.mesalab.knowledge.service;

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
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.knowledge.entity.*;
import com.mesalab.knowledge.enums.DataSourceEnum;
import com.mesalab.knowledge.enums.MatchEnum;
import com.mesalab.knowledge.enums.RangeEnum;
import com.mesalab.knowledge.strategy.QueryProvider;
import com.zdjizhi.utils.DateUtils;
import com.zdjizhi.utils.JsonMapper;
import com.zdjizhi.utils.StringUtil;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.io.IOException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
        requireNonNull(dslQuery, "query is null");
        Parameters parameters = dslQuery.getParameters();

        requireNonNull(dslQuery.getDataSource(), "dataSource is null");
        requireNonNull(EnumUtils.getEnum(DataSourceEnum.class,StringUtil.upperCase(dslQuery.getDataSource())), "dataSource no match");

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
                Validate.isTrue(EnumUtils.isValidEnum(MatchEnum.class, StringUtil.upperCase(m.getType())), "match type is illegal");
                //正则匹配方式 禁止 $xxx* 形式的value
                if (MatchEnum.REGEX.getType().equals(m.getType())) {
                    m.getFieldValues().forEach(mv -> {
                        Validate.isTrue(!(String.valueOf(mv).startsWith("$") && String.valueOf(mv).endsWith("*")), "error in matching sign. begin with * and do not end with * for suffix matching, end with * and do not begin with $ or * for prefix matching, begin with $ and do not end with * for exactly matching, begin with * and end with * or no wildcard for substring matching");
                    });
                }
            });
        }
        if (!ObjectUtils.isEmpty(ranges)) {
            ranges.forEach(r -> {
                Validate.isTrue(EnumUtils.isValidEnum(RangeEnum.class, StringUtil.upperCase(r.getType())), "range type is illegal");
            });
        }
        if (!ObjectUtils.isEmpty(sorts)) {
            sorts.forEach(s -> {
                Validate.isTrue("asc".equalsIgnoreCase(s.getType()) || "desc".equalsIgnoreCase(s.getType()), "sort type is illegal");
            });
        }
        if (!ObjectUtils.isEmpty(intervals)) {
            List<String> times = Splitter.on("/").splitToList(intervals.get(0));
            Validate.notEmpty(times, "intervals should be [startTime/endTime],such as 2020-01-01 00:00:00/2020-02-02 00:00:00");
            Validate.isTrue(times.size() == 2, "intervals should be [startTime/endTime],such as 2020-01-01 00:00:00/2020-02-02 00:00:00");
            Validate.notBlank(times.get(0), "start time is null");
            Validate.notBlank(times.get(1), "end time is null");
            Date start = DateUtils.convertStringToDate(times.get(0), DateUtils.YYYY_MM_DD_HH24_MM_SS);
            Date end = DateUtils.convertStringToDate(times.get(1), DateUtils.YYYY_MM_DD_HH24_MM_SS);
            Validate.isTrue(start.before(end), "the end time must be after the start time");
        }
        if (nonNull(limit)) {
            List<String> limits = Splitter.on(",").trimResults().omitEmptyStrings().splitToList(limit);
            Validate.notEmpty(limits, "limit is illegal");
            Validate.isTrue(!(limit.contains(",") && limits.size() != 2), "limit is illegal");
            for (String l : limits) {
                Validate.isTrue(l.length() <= maxrows.length() && Integer.valueOf(maxrows) >= Integer.valueOf(l) && Integer.valueOf(l) > 0, "limit is greater than 0 and less than " + maxrows);
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
            log.error(e.getMessage());
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

}
