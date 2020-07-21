package com.mesalab.knowledge.router;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mesalab.knowledge.common.config.ArangoConfig;
import com.mesalab.knowledge.common.utils.HttpClientUtils;
import com.mesalab.knowledge.entity.DSLObject;
import com.mesalab.knowledge.entity.Match;
import com.mesalab.knowledge.entity.Parameters;
import com.mesalab.knowledge.enums.DataSourceRouteEnum;
import com.zdjizhi.utils.DateUtils;
import com.zdjizhi.utils.JsonMapper;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.springframework.http.HttpHeaders;
import org.springframework.util.ObjectUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @description:
 * @author: zhq
 * @create: 2020-07-17
 **/
public class SubscriberIdAQLProviderImpl implements QLProvider {

    @Override
    public Object query(DSLObject.DSLQuery dslQeruy, ArangoConfig arangoConfig) {

        String datasource = dslQeruy.getDataSource();

        if (Objects.nonNull(DataSourceRouteEnum.valueOf(datasource))) {
            //1.构建查询语句 match like
            StringBuffer likeFilter = new StringBuffer();
            Parameters parameters = dslQeruy.getParameters();
            List<Match> matches = parameters.getMatch();
            likeFilter.append(Match.like(matches, (m) -> m != null && "SUBSCRIBER_ID".equals(m.getFieldKey()) && !ObjectUtils.isEmpty(m.getFieldValues())));

            StringBuffer aqlsb = new StringBuffer("for doc in SUBSCRIBER ");
            aqlsb.append(ObjectUtils.isEmpty(likeFilter) ? "" : likeFilter);
            aqlsb.append(" for v,e,p in 1..1 outbound doc._id graph 'SUBSCRIBER_IP_GRAPH' ");
            aqlsb.append(" filter doc.LAST_FOUND_TIME == e.LAST_FOUND_TIME ");
            aqlsb.append(" limit ").append(ObjectUtils.isEmpty(parameters.getLimit()) ? 10000 : parameters.getLimit());

            aqlsb.append(" return p ");
            Map queryMap = ImmutableMap.of("query", aqlsb.toString());
            Header header = new BasicHeader(HttpHeaders.AUTHORIZATION, arangoConfig.getJwt());
            String res = HttpClientUtils.httpPost(Joiner.on("").join(arangoConfig.getServer(), "/_db/", arangoConfig.getDatabase(), "/_api/cursor"), JsonMapper.toJsonString(queryMap), header);
            //2.解析结果
            Map jsonMap = (Map) JsonMapper.fromJsonString(res, Map.class);
            Object result = jsonMap.get("result");
            List<Map> resultList = new ObjectMapper().convertValue(result, new TypeReference<List<Map>>() {
            });
            return parseResult(resultList);
        }

        return null;
    }

    private Object parseResult(List<Map> resultList) {
        List<Object> list = Lists.newArrayList();
        for (Map<String, List<Map>> map : resultList) {
            Map<String, Object> newMap = Maps.newHashMap();
            Map subId = map.get("vertices").get(0);
            newMap.put("SUBSCRIBER_ID", subId.get("_key"));
            newMap.put("FIRST_FOUND_TIME", DateUtils.convertTimestampToString(Long.valueOf(String.valueOf(subId.get("FIRST_FOUND_TIME"))), DateUtils.YYYY_MM_DD_HH24_MM_SS));
            newMap.put("LAST_FOUND_TIME", DateUtils.convertTimestampToString(Long.valueOf(String.valueOf(subId.get("LAST_FOUND_TIME"))), DateUtils.YYYY_MM_DD_HH24_MM_SS));
            Map ip = map.get("vertices").get(1);
            newMap.put("IP", ip.get("IP"));
            list.add(newMap);
        }
        return list;
    }
}