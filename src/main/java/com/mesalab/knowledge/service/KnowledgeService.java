package com.mesalab.knowledge.service;

import com.google.common.base.Splitter;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.knowledge.entity.*;
import com.mesalab.knowledge.enums.DataSourceEnum;
import com.mesalab.knowledge.enums.MatchEnum;
import com.mesalab.knowledge.enums.RangeEnum;
import com.mesalab.knowledge.strategy.QueryProvider;
import com.zdjizhi.utils.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.*;

/**
 * @description: for IP learning
 * @author: zhq
 * @create: 2020-07-06
 **/
@Slf4j
@Service
public class KnowledgeService extends BaseService {

    @Autowired
    Map<String, QueryProvider> provider = new ConcurrentHashMap<>();

    public BaseResult query(DSLObject dSLObject) {
        //0.校验
        validate(dSLObject);
        DSLObject.DSLQuery dslQeruy = dSLObject.getQuery();
        //1.登录
        arangoConfig.setJwt(getJwt());
        //2.查询
        Object result = provider.get(dslQeruy.getDataSource()).query(dslQeruy, arangoConfig);
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
        requireNonNull(DataSourceEnum.match(dslQuery.getDataSource()), "dataSource no match");

        if(isNull(parameters)){
            return;
        }

        List<Match> matchs = parameters.getMatch();
        List<Range> ranges = parameters.getRange();
        List<String> intervals = parameters.getIntervals();
        List<Sort> sorts = parameters.getSort();
        String limit = parameters.getLimit();
        if (!ObjectUtils.isEmpty(matchs)) {
            matchs.forEach(m -> {
                Validate.notBlank(m.getType(), "match type is null");
                requireNonNull(MatchEnum.match(m.getType()), "match type is illegal");
                Validate.notBlank(m.getFieldKey(), "match fieldKey is null");
                Validate.notEmpty(m.getFieldValues(), "match fieldValues is empty");
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
                Validate.notBlank(r.getType(), "range type is null");
                requireNonNull(RangeEnum.match(r.getType()), "range type is illegal");
                Validate.notBlank(r.getFieldKey(), "range fieldKey is null");
                Validate.notEmpty(r.getFieldValues(), "range fieldValues is null");
            });
        }
        if (!ObjectUtils.isEmpty(sorts)) {
            sorts.forEach(s -> {
                Validate.notBlank(s.getType(), "sort type is null");
                Validate.notBlank(s.getFieldKey(), "sort fieldKey is null");
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
        }
    }

}
