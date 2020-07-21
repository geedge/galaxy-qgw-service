package com.mesalab.knowledge.service;

import com.google.common.base.Splitter;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.knowledge.entity.*;
import com.mesalab.knowledge.enums.DataSourceRouteEnum;
import com.mesalab.knowledge.enums.MatchEnum;
import com.mesalab.knowledge.enums.RangeEnum;
import com.zdjizhi.utils.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Date;
import java.util.List;

import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

/**
 * @description: for IP learning
 * @author: zhq
 * @create: 2020-07-06
 **/
@Slf4j
@Service
public class KnowledgeService extends BaseService {


    public BaseResult query(DSLObject dSLObject) {
        DSLObject.DSLQuery dslQeruy = dSLObject.getQuery();
        //0.校验
        validate(dslQeruy);
        //1.登录
        arangoConfig.setJwt(getJwt());
        //2.查询
        Object result = DataSourceRouteEnum.match(dslQeruy.getDataSource()).getRouter().query(dslQeruy, arangoConfig);

        return BaseResultGenerator.success(result);

    }
    /**
     * @Author zhq
     * @Description //TODO 参数校验
     * @Date 2020\7\21 0021
     * @Param [dslQuery]
     * @return void
     **/
    private void validate(DSLObject.DSLQuery dslQuery) {

        String datasource = dslQuery.getDataSource();
        Parameters parameters = dslQuery.getParameters();

        if(nonNull(datasource)){
            throw new BusinessException("DataSource is null");
        }
        requireNonNull(DataSourceRouteEnum.match(datasource), "DataSource no match");
        requireNonNull(parameters, "Parameters is null");

        List<Match> matchs = parameters.getMatch();
        List<Range> ranges = parameters.getRange();
        List<String> intervals = parameters.getIntervals();
        List<Sort> sorts = parameters.getSort();
        String limit = parameters.getLimit();
        if (!ObjectUtils.isEmpty(matchs)) {
            matchs.forEach(m -> {
                requireNonNull(m.getType(), "type is null");
                requireNonNull(MatchEnum.match(m.getType()), "match type is illegal");
                requireNonNull(m.getFieldKey(), "fieldKey is null");
                if (ObjectUtils.isEmpty(m.getFieldValues())) {
                    throw new BusinessException("fieldValues is empty");
                }
                //正则匹配方式 禁止 $xxx* 形式的value
                if (MatchEnum.REGEX.getType().equals(m.getType())) {
                    m.getFieldValues().forEach(mv -> {
                        if (String.valueOf(mv).startsWith("$") && String.valueOf(mv).endsWith("*")) {
                            throw new BusinessException("error in matching sign. begin with * and do not end with * for suffix matching, end with * and do not begin with $ or * for prefix matching, begin with $ and do not end with * for exactly matching, begin with * and end with * or no wildcard for substring matching");
                        }
                    });
                }
            });
        }
        if (!ObjectUtils.isEmpty(ranges)) {
            ranges.forEach(r -> {
                requireNonNull(r.getType(), "type is null");
                requireNonNull(RangeEnum.match(r.getType()), "range type is illegal");
                requireNonNull(r.getFieldKey(), "fieldKey is null");
                requireNonNull(r.getFieldValues(), "fieldValues is null");
            });
        }
        if (!ObjectUtils.isEmpty(sorts)) {
            sorts.forEach(s -> {
                requireNonNull(s.getType(), "type is null");
                requireNonNull(s.getFieldKey(), "fieldKey is null");
            });
        }
        if (ObjectUtils.isEmpty(intervals)) {
            throw new BusinessException("intervals is null");
        } else {
            List<String> times = Splitter.on("/").trimResults().omitEmptyStrings().splitToList(intervals.get(0));
            requireNonNull(times, "intervals should be [startTime/endTime]");
            if(times.size()!=2) {throw new BusinessException("intervals should be [startTime/endTime]");}
            requireNonNull(times.get(0), "start time is null");
            requireNonNull(times.get(1), "end time is null");
            Date start = DateUtils.convertStringToDate(times.get(0), DateUtils.YYYY_MM_DD_HH24_MM_SS);
            Date end = DateUtils.convertStringToDate(times.get(1), DateUtils.YYYY_MM_DD_HH24_MM_SS);
            if (start.after(end)) {
                throw new BusinessException("the end time must be after the start time");
            }
        }
        if (nonNull(limit)) {
            List<String> limits = Splitter.on(",").trimResults().omitEmptyStrings().splitToList(limit);
            if (ObjectUtils.isEmpty(limits)) {
                throw new BusinessException("limit is illegal");
            }
        }
    }


}
