package com.mesalab.knowledge.strategy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mesalab.knowledge.entity.DSLObject;
import com.mesalab.knowledge.entity.Match;
import com.mesalab.knowledge.entity.Parameters;
import com.mesalab.knowledge.entity.Range;
import com.mesalab.knowledge.entity.arango.IpLearningPath;
import com.mesalab.knowledge.enums.RangeEnum;
import com.zdjizhi.utils.DateUtils;
import com.zdjizhi.utils.StringUtil;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 根据fqdn查询ip的关系
 */

@Component("IP_LEARNING_VIEW")
public class FqdnProviderImpl implements QueryProvider {

    private static final String depthregex = "^[1-9]\\d*$"; //depth 取值范围大于0
    private static final String SSL = "SSL";
    private static final String TLS = "TLS";
    private static final String HTTP = "HTTP";
    private static final String DNS = "DNS";

    @Value("${arango.maxrows}")
    private String limit;//查询最大条数

    @Autowired
    BaseQueryProvider baseQueryProvider;

    @Override
    public Object query(DSLObject.DSLQuery dslQuery) {
        Parameters parameters = dslQuery.getParameters();
        //a.处理条件 b.需要的条件：fqdn_name 多个, depth, filter:last_found_time, dist_cip, http,ssl/tls,dns, limit
        StringBuffer likeFilter = new StringBuffer();
        StringBuffer intervalsb = new StringBuffer();
        StringBuffer protocolsb = new StringBuffer();
        StringBuffer distCiptsb = new StringBuffer();
        if(!ObjectUtils.isEmpty(parameters)){
            limit = ObjectUtils.isEmpty(parameters.getLimit()) ? limit : parameters.getLimit();
            List<Match> matches = parameters.getMatch();
            //1.构建查询语句 match like
            likeFilter.append(Match.like(matches, (m) -> m != null && "FQDN_NAME".equals(m.getFieldKey()) && !ObjectUtils.isEmpty(m.getFieldValues())).replace(" doc._key like \"", " e._from like \"FQDN/"));

            //2.构建查询语句 intervals 时间
            List<String> intervals = parameters.getIntervals();
            if(!ObjectUtils.isEmpty(intervals)){
                String[] times = intervals.get(0).split("/");
                intervalsb.append(" and e.LAST_FOUND_TIME >= ").append(DateUtils.convertStringToTimestamp(times[0], DateUtils.YYYY_MM_DD_HH24_MM_SS)).append(" and ").append("e.LAST_FOUND_TIME <").append(DateUtils.convertStringToTimestamp(times[1], DateUtils.YYYY_MM_DD_HH24_MM_SS));
            }

            //3.构建查询语句 range depth ,protocols, limit
            List<Range> ranges = parameters.getRange();

            if(!ObjectUtils.isEmpty(ranges)){

                for (Range r : ranges) {
                    //查询深度
                    if ("DEPTH".equals(r.getFieldKey())) {
                        Validate.notEmpty(r.getFieldValues(), "fieldValues of depth is null");
                        Validate.matchesPattern(String.valueOf(r.getFieldValues().get(0)), depthregex, "fieldValues of depth is illegal, you should type in an integer greater than 0");
                    } else if ("PROTOCOL".equals(r.getFieldKey())) {
                        List<String> protocols = Lists.newArrayList();
                        for (Object protocol : r.getFieldValues()) {
                            if (HTTP.equals(protocol)) {
                                protocols.add("e.HTTP_CNT_TOTAL > 0");
                            } else if (DNS.equals(protocol)) {
                                protocols.add("e.DNS_CNT_TOTAL > 0");
                            } else if (SSL.equals(protocol)||TLS.equals(protocol)) {
                                protocols.add("e.TLS_CNT_TOTAL > 0");
                            }
                        }
                        if (protocols.size() > 0) {
                            protocolsb.append(Joiner.on("").skipNulls().join(" and (", Joiner.on(" or ").skipNulls().join(protocols), ")"));
                        }
                    } else if ("UNIQ_CIP".equals(r.getFieldKey())) {
                        distCiptsb.append(" and count(e.DIST_CIP) ").append(EnumUtils.getEnum(RangeEnum.class, StringUtil.upperCase(r.getType())).getSymbol()).append(r.getFieldValues().get(0));
                    }

                }
            }
        }

        StringBuffer aqlsb = new StringBuffer("for e in R_LOCATE_FQDN2IP ");
        aqlsb.append(ObjectUtils.isEmpty(likeFilter) ? "" : likeFilter);
        aqlsb.append(" filter 1==1 ");
        aqlsb.append(intervalsb);
        aqlsb.append(distCiptsb);
        aqlsb.append(protocolsb);

        int pageRow = 0;
        int offSet = 0;
        int pageSize = 0;
        List<String> limitList = Splitter.on(",").trimResults().omitEmptyStrings().splitToList(limit);
        if(limitList.size()<2){
            pageSize= pageRow = Integer.valueOf(limitList.get(0));
        } else {
            pageRow = Integer.valueOf(limitList.get(0)) + Integer.valueOf(limitList.get(1));
            offSet = Integer.valueOf(limitList.get(0));
            pageSize = Integer.valueOf(limitList.get(1));
        }

        List<IpLearningPath.IplearningEdges> ipEdgesList = getDistinctList(aqlsb, pageRow, offSet, pageSize);

        return parseResult(ipEdgesList);

    }
    //循环查询 ，目的为了数量达到limit , 结果数据是否为limit , 1.当查询数量小于limit停止 2.当结果数量=limit停止
    private List<IpLearningPath.IplearningEdges> getDistinctList(StringBuffer aqlsb ,int pageRow,int offSet, int pageSize){
        pageRow = pageRow + offSet + pageSize;//每次多查一点点，去重
        StringBuffer sb  = new StringBuffer(aqlsb);
        sb.append(" limit ").append(pageRow);
        sb.append(" return distinct e ");

        Object result = baseQueryProvider.queryArango(sb.toString());
        List ipEdgesList = new ObjectMapper().convertValue(result, new TypeReference<List<IpLearningPath.IplearningEdges>>() {
        });
        List<IpLearningPath.IplearningEdges> edgesList = (List<IpLearningPath.IplearningEdges>) ipEdgesList.stream().collect(Collectors.collectingAndThen(Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(IpLearningPath.IplearningEdges::getTo))), ArrayList::new));
        if(ipEdgesList.size()<pageRow || edgesList.size()>= pageSize+offSet){
            if(offSet > edgesList.size()){
                return new ArrayList<>();
            } else {
                if(offSet + pageSize > edgesList.size()){
                    return edgesList.subList(offSet, edgesList.size());
                }
                return edgesList.subList(offSet, offSet + pageSize);
            }
        }
        return getDistinctList( aqlsb , pageRow, offSet, pageSize);
    }
    /**
     * @return java.lang.Object
     * @Author zhq
     * @Description //TODO 返回需要的结果集
     * @Date 2020\7\15 0015
     * @Param [IpLearningPath]
     **/
    private Object parseResult(List<IpLearningPath.IplearningEdges> edgesList) {

            List<Object> list = Lists.newArrayList();
            if(!ObjectUtils.isEmpty(edgesList)){
                for (IpLearningPath.IplearningEdges edges : edgesList) {
                    Map<String, Object> map = Maps.newHashMap();
                    List<String> protocolsList = Lists.newArrayList();
                    map.put("FQDN_NAME", CharMatcher.anyOf("FQDN/").trimLeadingFrom(edges.getFrom()));
                    map.put("IP", CharMatcher.anyOf("IP/").trimLeadingFrom(edges.getTo()));
                    map.put("FIRST_FOUND_TIME", DateUtils.convertTimestampToString(edges.getFirstFoundTime(), DateUtils.YYYY_MM_DD_HH24_MM_SS));
                    map.put("LAST_FOUND_TIME", DateUtils.convertTimestampToString(edges.getLastFoundTime(), DateUtils.YYYY_MM_DD_HH24_MM_SS));
                    //协议 按 TLS_CNT_TOTAL>0,SESSION_RECENT按 *_CNT_RECENT和，SESSION_TOTAL按 *_CNT_TOTAL和
                    if (edges.getHttpCntTotal() > 0) {
                        protocolsList.add(HTTP);
                    }
                    if (edges.getTlsCntTotal() > 0) {
                        protocolsList.add(SSL);
                    }
                    if (edges.getDnsCntTotal() > 0) {
                        protocolsList.add(DNS);
                    }
                    map.put("PROTOCOL", Joiner.on(",").join(protocolsList));
                    map.put("SESSION_TOTAL", edges.getHttpCntTotal() + edges.getTlsCntTotal() + edges.getDnsCntTotal());
                    map.put("SESSION_RECENT", Stream.of(edges.getHttpCntRecent(), edges.getTlsCntRecent(), edges.getDnsCntRecent()).flatMap(Collection::stream).mapToLong(x -> x).sum());

                    //UNIQ_CIP_RECENT与UNIQ_CIP相同
                    map.put("UNIQ_CIP", edges.getDistCip()==null?0:edges.getDistCip().size());
                    map.put("UNIQ_CIP_RECENT", edges.getDistCip()==null?0:edges.getDistCip().size());

                    list.add(map);
                }
            }

        return list;
    }

}
