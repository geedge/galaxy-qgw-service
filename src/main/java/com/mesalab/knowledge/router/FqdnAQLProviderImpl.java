package com.mesalab.knowledge.router;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.knowledge.common.config.ArangoConfig;
import com.mesalab.knowledge.common.utils.HttpClientUtils;
import com.mesalab.knowledge.entity.DSLObject;
import com.mesalab.knowledge.entity.Match;
import com.mesalab.knowledge.entity.Parameters;
import com.mesalab.knowledge.entity.Range;
import com.mesalab.knowledge.entity.arango.IpLearningPath;
import com.mesalab.knowledge.enums.DataSourceRouteEnum;
import com.mesalab.knowledge.enums.RangeEnum;
import com.zdjizhi.utils.DateUtils;
import com.zdjizhi.utils.JsonMapper;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.springframework.http.HttpHeaders;
import org.springframework.util.ObjectUtils;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

/**
 * 根据fqdn查询ip的关系
 */
public class FqdnAQLProviderImpl implements QLProvider {
    public static void main(String[] args) {
        System.out.println("1..1".matches("^\\d..\\d$"));
    }
    @Override
    public Object query(DSLObject.DSLQuery dslQuery, ArangoConfig arangoConfig) {
        String datasource = dslQuery.getDataSource();
        Parameters parameters = dslQuery.getParameters();

        if (nonNull(DataSourceRouteEnum.valueOf(datasource))) {

            //a.处理条件 b.需要的条件：fqdn_name 多个, depth, filter:last_found_time, dist_cip, http,tls,dns, limit

            //1.构建查询语句 match like
            StringBuffer likeFilter = new StringBuffer();
            List<Match> matches = parameters.getMatch();
            //match.like
            likeFilter.append(Match.like(matches, (m) -> m != null && "FQDN_NAME".equals(m.getFieldKey()) && !ObjectUtils.isEmpty(m.getFieldValues())));

            //2.构建查询语句 intervals 时间
            List<String> intervals = parameters.getIntervals();

            if(ObjectUtils.isEmpty(intervals)){
                throw new BusinessException("intervals is empty");
            }
            String[] times = intervals.get(0).split("/");
            StringBuffer intervalsb = new StringBuffer("filter e.FIRST_FOUND_TIME >= ").append(DateUtils.convertStringToTimestamp(times[0], DateUtils.YYYY_MM_DD_HH24_MM_SS)).append(" and ").append("e.LAST_FOUND_TIME <").append(DateUtils.convertStringToTimestamp(times[1], DateUtils.YYYY_MM_DD_HH24_MM_SS));

            //3.构建查询语句 range depth ,protocols, limit
            List<Range> ranges = parameters.getRange();
            Object depth = "";
            StringBuffer protocolsb = new StringBuffer();
            StringBuffer distCiptsb = new StringBuffer();
            List<String> protocols = Lists.newArrayList();

            requireNonNull(ranges,"range is null");
            for (Range r : ranges) {
                //查询深度
                if ("DEPTH".equals(r.getFieldKey())) {
                    Object o = r.getFieldValues().get(0);
                    if(ObjectUtils.isEmpty(r.getFieldValues())){
                        throw new BusinessException("fieldValues of depth is null");
                    }
                    if (String.valueOf(r.getFieldValues().get(0)).matches("^\\d..\\d$")) {
                        throw new BusinessException("fieldValues of depth is illegal, you should type in such as 1..1");
                    }
                    depth = r.getFieldValues().stream().findFirst().get();
                } else if ("PROTOCOL".equals(r.getFieldKey())) {
                    String symbol = RangeEnum.match(r.getType()).symbol();
                    for (Object protocol : r.getFieldValues()) {
                        if ("HTTP".equals(protocol)) {
                            protocols.add("e.HTTP_CNT_TOTAL " + symbol + " 0");
                        } else if ("DNS".equals(protocol)) {
                            protocols.add("e.DNS_CNT_TOTAL " + symbol + " 0");
                        } else if ("TLS".equals(protocol)) {
                            protocols.add("e.TLS_CNT_TOTAL " + symbol + " 0");
                        }
                    }
                    if (protocols.size() > 0) {
                        protocolsb.append(Joiner.on("").skipNulls().join(" and (", Joiner.on(" or ").skipNulls().join(protocols), ")"));
                    }
                } else if ("UNIQ_CIP".equals(r.getFieldKey())) {
                    distCiptsb.append(" and count(e.DIST_CIP) ").append(RangeEnum.match(r.getType()).symbol()).append(r.getFieldValues().stream().findFirst());
                }

            }

            StringBuffer aqlsb = new StringBuffer("let FQS = ( for doc in FQDN ");
            aqlsb.append(ObjectUtils.isEmpty(likeFilter) ? "" : likeFilter);
            aqlsb.append(" for fq2fq in ");
            aqlsb.append(depth);
            aqlsb.append(" outbound doc._id R_SAME_ORIGIN_FQDN2FQDN ");
            aqlsb.append(" return distinct fq2fq ) ");
            aqlsb.append(" for fq in FQS ");
            aqlsb.append(" for v,e,p in 1..1 outbound fq._id graph 'FQDN_IP_GRAPH' ");
            aqlsb.append(intervalsb);
            aqlsb.append(ObjectUtils.isEmpty(distCiptsb) ? "" : distCiptsb);
            aqlsb.append(ObjectUtils.isEmpty(protocolsb) ? "" : protocolsb);
            aqlsb.append(" limit ").append(ObjectUtils.isEmpty(parameters.getLimit()) ? 10000 : parameters.getLimit());
            aqlsb.append(" return distinct p ");

            Map queryMap = ImmutableMap.of("query", aqlsb.toString());
            Header header = new BasicHeader(HttpHeaders.AUTHORIZATION, arangoConfig.getJwt());
            String res = HttpClientUtils.httpPost(Joiner.on("").join(arangoConfig.getServer(), "/_db/", arangoConfig.getDatabase(), "/_api/cursor"), JsonMapper.toJsonString(queryMap), header);
            //2.解析结果
            Map jsonMap = (Map) JsonMapper.fromJsonString(res, Map.class);
            Object result = jsonMap.get("result");
            List IpPathList = new ObjectMapper().convertValue(result, new TypeReference<List<IpLearningPath>>() {
            });
            return parseResult(IpPathList);
        }

        return null;

    }


    /**
     * @return java.lang.Object
     * @Author zhq
     * @Description //TODO 返回需要的结果集
     * @Date 2020\7\15 0015
     * @Param [IpLearningPath]
     **/
    private Object parseResult(List<IpLearningPath> pathList) {

        List<Object> list = Lists.newArrayList();
        for (IpLearningPath path : pathList) {
            for (IpLearningPath.IplearningEdges edges : path.getEdges()) {
                Map<String, Object> map = Maps.newHashMap();
                List<String> protocolsList = Lists.newArrayList();
                map.put("FQDN", CharMatcher.anyOf("FQDN/").trimLeadingFrom(edges.getFrom()));
                map.put("IP", CharMatcher.anyOf("IP/").trimLeadingFrom(edges.getTo()));
                map.put("FIRST_FOUND_TIME", DateUtils.convertTimestampToString(edges.getFirstFoundTime(), DateUtils.YYYY_MM_DD_HH24_MM_SS));
                map.put("LAST_FOUND_TIME", DateUtils.convertTimestampToString(edges.getLastFoundTime(), DateUtils.YYYY_MM_DD_HH24_MM_SS));
                //协议 按 TLS_CNT_TOTAL>0,SESSION_RECENT按 *_CNT_RECENT和，SESSION_TOTAL按 *_CNT_TOTAL和
                if (edges.getHttpCntTotal() > 0) {
                    protocolsList.add("HTTP");
                }
                if (edges.getTlsCntTotal() > 0) {
                    protocolsList.add("TLS");
                }
                if (edges.getDnsCntTotal() > 0) {
                    protocolsList.add("DNS");
                }
                map.put("PROTOCOL", Joiner.on(",").join(protocolsList));
                map.put("SESSION_TOTAL", edges.getHttpCntTotal() + edges.getTlsCntTotal() + edges.getDnsCntTotal());
                map.put("SESSION_RECENT", Stream.of(edges.getHttpCntRecent(), edges.getTlsCntRecent(), edges.getDnsCntRecent()).flatMap(Collection::stream).mapToLong(x -> x).sum());

                //UNIQ_CIP按DIST_CIP_TS.size()计算，UNIQ_CIP_RECENT按最近24小时计算
                map.put("UNIQ_CIP", edges.getDistCipTs().size());
                map.put("UNIQ_CIP_RECENT", edges.getDistCipTs().stream().filter(t -> t <= (DateUtils.getSomeHour(new Date(), -24).getTime() / 1000)).count());

                list.add(map);
            }

        }

        return list;
    }

}
