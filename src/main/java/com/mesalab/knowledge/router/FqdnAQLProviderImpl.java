package com.mesalab.knowledge.router;

import java.text.MessageFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.*;
import com.mesalab.knowledge.common.config.ArangoConfig;
import com.mesalab.knowledge.common.utils.HttpClientUtils;
import com.mesalab.knowledge.entity.*;
import com.mesalab.knowledge.entity.Range;
import com.mesalab.knowledge.entity.arango.IpLearningPath;
import com.mesalab.knowledge.graph.DatasourceRouteEnum;
import com.mesalab.knowledge.graph.QLProvider;
import com.mesalab.knowledge.parse.MatchEnum;
import com.mesalab.knowledge.parse.SymbolEnum;
import com.zdjizhi.utils.DateUtils;
import com.zdjizhi.utils.JsonMapper;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.springframework.http.HttpHeaders;
import org.springframework.util.ObjectUtils;

import static java.util.stream.Collectors.joining;

/**
 * 根据fqdn查询ip的关系
 */
public class FqdnAQLProviderImpl implements QLProvider {

    @Override
    public Object query(DSLObject.DSLQuery query, ArangoConfig arangoConfig) {
        String datasource = query.getDatasource();
        String queryType = query.getQueryType();
        Parameters parameters = query.getParameters();

        if (Objects.nonNull(DatasourceRouteEnum.valueOf(datasource))) {

            //0.校验

            //a.处理条件 b.需要的条件：fqdn_name 多个, depth, filter:last_found_time, dist_cip, http,tls,dns, limit

            //1.构建查询语句 match like
            StringBuffer likeFilter = new StringBuffer();
            List<Match> matches = parameters.getMatches();
            matches.stream().filter(m -> m != null && "FQDN_NAME".equals(m.getFiledKey())).forEach(m -> {
                MatchEnum match = MatchEnum.match(m.getType(), null);
                likeFilter.append(m.getFiledValues().stream().filter(mv -> mv != null).map(mv -> {
                    return MessageFormat.format(match.matchExp(), mv);
                }).collect(joining(" or doc._key like ", " filter ( doc._key like ", ") ")));
            });
            //2.构建查询语句 intervals 时间
            List<String> intervals = parameters.getIntervals();
            String[] times = intervals.get(0).split("/");
            StringBuffer intervalsb = new StringBuffer("filter e.FIRST_FOUND_TIME >= ").append(DateUtils.convertStringToTimestamp(times[0], DateUtils.YYYY_MM_DD_HH24_MM_SS)).append(" and ").append("e.LAST_FOUND_TIME <").append(DateUtils.convertStringToTimestamp(times[1], DateUtils.YYYY_MM_DD_HH24_MM_SS));

            //3.构建查询语句 range depth ,protocols, limit
            List<Range> ranges = parameters.getRanges();
            Object depth = "";
            StringBuffer protocolsb = new StringBuffer();
            StringBuffer distCiptsb = new StringBuffer();
            List<String> protocols = Lists.newArrayList();
            for (Range r : ranges) {
                //查询深度
                if ("DEPTH".equals(r.getFiledKey())) {
                    depth = r.getFiledValues().stream().findFirst().orElse("1..1");
                } else if ("PROTOCOL".equals(r.getFiledKey())) {
                    String symbol = SymbolEnum.match(r.getType()).symbol();
                    for (Object protocol : r.getFiledValues()) {
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
                } else if ("UNIQ_CIP".equals(r.getFiledKey())) {
                    distCiptsb.append(" and count(e.DIST_CIP) ").append(SymbolEnum.match(r.getType()).symbol()).append(r.getFiledValues().stream().findFirst());
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
            aqlsb.append(" limit ").append(ObjectUtils.isEmpty(parameters.getLimit()) ? parameters.getLimit() : 1000);
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

        /*
          "IP": "110.18.247.136",
          "FQDN_NAME": "itunes.apple.com",
          "PROTOCOL": "HTTP",
          "FIRST_FOUND_TIME": "2020-01-01 00:00:00",
          "LAST_FOUND_TIME": "2020-01-02 00:00:00",
          "UNIQ_CIP_RECENT": 1,
          "UNIQ_CIP": 12,
          "SESSION_RECENT": 2,
          "SESSION_TOTAL": 14
         */
        //1. 获取点边中需要的元素 2.
        List<Object> list = Lists.newArrayList();
        for (IpLearningPath path : pathList) {
            for (IpLearningPath.IplearningEdges edges : path.getEdges()) {
                Map<String, Object> map = Maps.newHashMap();
                List<String> protocolsList = Lists.newArrayList();
                map.put("FQDN", CharMatcher.anyOf(edges.getFrom()).trimLeadingFrom("FQDN/"));
                map.put("IP", CharMatcher.anyOf(edges.getTo()).trimLeadingFrom("IP/"));
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
                map.put("PROTOCOL",Joiner.on(",").join(protocolsList));
                map.put("SESSION_TOTAL",edges.getHttpCntTotal()+edges.getTlsCntTotal()+edges.getDnsCntTotal());
                map.put("SESSION_RECENT",Stream.of(edges.getHttpCntRecent(),edges.getTlsCntRecent(),edges.getDnsCntRecent()).flatMap(Collection::stream).mapToLong(x->x).sum());

                //UNIQ_CIP按DIST_CIP_TS.size()计算，UNIQ_CIP_RECENT按最近计算
                map.put("UNIQ_CIP",edges.getDistCipTs().size());
//                map.put("UNIQ_CIP_RECENT",);

            }
        }

        return null;
    }

}
