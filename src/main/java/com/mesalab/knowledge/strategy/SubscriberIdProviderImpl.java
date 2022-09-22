package com.mesalab.knowledge.strategy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mesalab.knowledge.entity.DSLObject;
import com.mesalab.knowledge.entity.Match;
import com.mesalab.knowledge.entity.Parameters;
import com.mesalab.knowledge.entity.Range;
import com.mesalab.knowledge.enums.RangeEnum;
import com.zdjizhi.utils.DateUtils;
import com.zdjizhi.utils.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @description:
 * @author: zhq
 * @create: 2020-07-17
 **/
@Component("SUBSCRIBER_ID_VIEW")
public class SubscriberIdProviderImpl implements QueryProvider {

    @Value("${arango.maxrows}")
    private String limit;//查询最大条数

    @Autowired
    BaseQueryProvider baseQueryProvider;

    @Override
    public Object query(DSLObject.DSLQuery dslQeruy) {

        //1.构建查询语句 match like
        StringBuffer likeFilter = new StringBuffer();
        StringBuffer intervalsb = new StringBuffer();
        List<Range> ranges = null;

        Parameters parameters = dslQeruy.getParameters();
        if(!ObjectUtils.isEmpty(parameters)){
            //构建range范围条件
            ranges = parameters.getRange();

            likeFilter.append(Match.like(parameters.getMatch(), (m) -> m != null && "SUBSCRIBER_ID".equals(m.getFieldKey()) && !ObjectUtils.isEmpty(m.getFieldValues()), ranges));
            limit = ObjectUtils.isEmpty(parameters.getLimit()) ? limit : parameters.getLimit();

            //构建查询语句 intervals 时间
            List<String> intervals = parameters.getIntervals();
            if(!ObjectUtils.isEmpty(intervals)){
                String[] times = intervals.get(0).split("/");
                intervalsb.append(" filter e.LAST_FOUND_TIME >= ").append(DateUtils.convertStringToTimestamp(times[0], DateUtils.YYYY_MM_DD_HH24_MM_SS)).append(" and ").append("e.LAST_FOUND_TIME <").append(DateUtils.convertStringToTimestamp(times[1], DateUtils.YYYY_MM_DD_HH24_MM_SS));
            }

        }

        StringBuffer aqlsb = new StringBuffer("for doc in SUBSCRIBER ");
        aqlsb.append(ObjectUtils.isEmpty(likeFilter) ? "" : likeFilter);
        aqlsb.append(ObjectUtils.isEmpty(ranges)?"":" filter "+ Range.getRangExp(ranges,"doc"));
        aqlsb.append(" let maxTime = (let times = (for v,e,p in 1 outbound doc._id graph 'SUBSCRIBER_IP_GRAPH' ");
        aqlsb.append(ObjectUtils.isEmpty(intervalsb)?"":" filter e."+intervalsb.toString());
        aqlsb.append(ObjectUtils.isEmpty(ranges)?"":" filter "+ Range.getRangExp(ranges,"e"));
        aqlsb.append("return e.LAST_FOUND_TIME) return max(times)) ");

        aqlsb.append(" for v,e,p in 1..1 outbound doc._id graph 'SUBSCRIBER_IP_GRAPH' ");
        aqlsb.append(" filter e.LAST_FOUND_TIME == maxTime[0] ");
        aqlsb.append(ObjectUtils.isEmpty(ranges)?"":" and "+ Range.getRangExp(ranges,"e"));
        aqlsb.append(" limit ").append(limit);
        aqlsb.append(" return distinct p ");

        //1.查询
        Object result = baseQueryProvider.queryArango(aqlsb.toString());
        //2.解析结果
        List<Map> resultList = new ObjectMapper().convertValue(result, new TypeReference<List<Map>>() {
        });
        return parseResult(resultList);

    }

    private Object parseResult(List<Map> resultList) {
        List<Object> list = Lists.newArrayList();
        for (Map<String, List<Map>> map : resultList) {
            Map<String, Object> newMap = Maps.newHashMap();
            Map subIdVertice = map.get("vertices").get(0);
            if (Objects.isNull(subIdVertice)) {
                continue;
            }
            newMap.put("SUBSCRIBER_ID", String.valueOf(subIdVertice.get("SUBSCRIBER")));
            newMap.put("FIRST_FOUND_TIME", DateUtils.convertTimestampToString(Long.valueOf(String.valueOf(subIdVertice.get("FIRST_FOUND_TIME"))), DateUtils.YYYY_MM_DD_HH24_MM_SS));
            newMap.put("LAST_FOUND_TIME", DateUtils.convertTimestampToString(Long.valueOf(String.valueOf(subIdVertice.get("LAST_FOUND_TIME"))), DateUtils.YYYY_MM_DD_HH24_MM_SS));
            Map ipEdges = map.get("edges").get(0);
            if (Objects.isNull(ipEdges)||Objects.isNull(ipEdges.get("IP"))) {
                newMap.put("IP", "");
            } else {
                newMap.put("IP", String.valueOf(ipEdges.get("IP")));
            }
            list.add(newMap);
        }
        return list;
    }

}