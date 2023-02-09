package com.mesalab.tsg.strategy;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mesalab.tsg.entity.DSLObject;
import com.mesalab.tsg.entity.Match;
import com.mesalab.tsg.entity.Parameters;
import com.mesalab.tsg.entity.Range;
import com.mesalab.qgw.dialect.BaseQueryProvider;
import com.zdjizhi.utils.DateUtils;
import org.joda.time.format.ISODateTimeFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * @description:
 * @author: zhq
 * @create: 2020-07-17
 **/
@Component("SUBSCRIBER_ID_VIEW")
public class SubscriberIdProviderImpl implements QueryProvider {

    private static final Log log = LogFactory.get();
    public static final Pattern strFormatDateTime = Pattern.compile("\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}", Pattern.CASE_INSENSITIVE);
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
                long startTime;
                long endTime;
                String start = times[0];
                String end = times[1];
                if (strFormatDateTime.matcher(start).find()){
                    startTime = DateUtils.convertStringToTimestamp(start, "yyyy-MM-dd HH:mm:ss");
                } else {
                    startTime = ISODateTimeFormat.dateTimeNoMillis().parseMillis(start)/1000;
                }
                if (strFormatDateTime.matcher(end).find()){
                    endTime = DateUtils.convertStringToTimestamp(end, "yyyy-MM-dd HH:mm:ss");
                } else {
                    endTime = ISODateTimeFormat.dateTimeNoMillis().parseMillis(end)/1000;
                }
                intervalsb.append(" filter e.LAST_FOUND_TIME >= ").append(startTime).append(" and ").append("e.LAST_FOUND_TIME < ").append(endTime);
            }

        }

        StringBuffer aqlsb = new StringBuffer("for doc in SUBSCRIBER ");
        aqlsb.append(ObjectUtils.isEmpty(likeFilter) ? "" : likeFilter);
        aqlsb.append(ObjectUtils.isEmpty(ranges)?"":" filter "+ Range.getRangExp(ranges,"doc"));
        aqlsb.append(" let maxTime = (let times = (for v,e,p in 1 outbound doc._id graph 'SUBSCRIBER_IP_GRAPH' ");
        aqlsb.append(ObjectUtils.isEmpty(intervalsb) ? "" : intervalsb);
        aqlsb.append(ObjectUtils.isEmpty(ranges)?"":" filter "+ Range.getRangExp(ranges,"e"));
        aqlsb.append("return e.LAST_FOUND_TIME) return max(times)) ");

        aqlsb.append(" for v,e,p in 1..1 outbound doc._id graph 'SUBSCRIBER_IP_GRAPH' ");
        aqlsb.append(" filter e.LAST_FOUND_TIME == maxTime[0] ");
        aqlsb.append(ObjectUtils.isEmpty(ranges)?"":" and "+ Range.getRangExp(ranges,"e"));
        aqlsb.append(" limit ").append(limit);
        aqlsb.append(" return distinct p ");
        log.info("query knowledge subscriber id aql: {}", aqlsb);

        //1.查询
        Object result = baseQueryProvider.queryArango(aqlsb.toString());
        //2.解析结果
        List<Map> resultList = new ObjectMapper().convertValue(result, new TypeReference<List<Map>>() {
        });
        log.info("query knowledge subscriber id result size: {}", resultList.size());
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
            newMap.put("VSYS_ID", subIdVertice.get("VSYS_ID"));
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