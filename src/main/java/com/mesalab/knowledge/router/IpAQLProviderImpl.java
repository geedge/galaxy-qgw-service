package com.mesalab.knowledge.router;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.mesalab.knowledge.common.config.ArangoConfig;
import com.mesalab.knowledge.common.utils.HttpClientUtils;
import com.mesalab.knowledge.entity.DSLObject;
import com.mesalab.knowledge.entity.Parameters;
import com.mesalab.knowledge.entity.Sort;
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
import java.util.stream.Collectors;

/**
 * @description: for ip search
 * @author: zhq
 * @create: 2020-07-17
 **/
public class IpAQLProviderImpl implements QLProvider {


    @Override
    public Object query(DSLObject.DSLQuery dslQeruy, ArangoConfig arangoConfig) {
        String datasource = dslQeruy.getDataSource();
        Parameters parameters = dslQeruy.getParameters();

        if (Objects.nonNull(DataSourceRouteEnum.valueOf(datasource))) {
            //需要条件  时间，limit
            List<Sort> sorts = parameters.getSort();
            StringBuffer aqlsb = new StringBuffer();
            String sortAql = sorts.stream().map(x -> {
                if ("BYTES_TOTAL".equals(x.getFieldKey())) {
                    return "(doc.CLIENT_BYTES_SUM+doc.SERVER_BYTES_SUM) " + x.getType();
                } else if ("LAST_FOUND_TIME".equals(x.getFieldKey())) {
                    return "doc.LAST_FOUND_TIME " + x.getType();
                }
                return "";
            }).collect(Collectors.joining(",", " sort ", " "));
            aqlsb.append("for doc in IP ");
            aqlsb.append(sortAql);
            aqlsb.append(" limit ").append(ObjectUtils.isEmpty(parameters.getLimit()) ? 10000 : parameters.getLimit());
            aqlsb.append(" return doc");
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
        for (Map ip : resultList) {
            ip.remove("_key");
            ip.remove("_id");
            ip.remove("_rev");
            ip.put("FIRST_FOUND_TIME", DateUtils.convertTimestampToString(Long.valueOf(String.valueOf(ip.get("FIRST_FOUND_TIME"))), DateUtils.YYYY_MM_DD_HH24_MM_SS));
            ip.put("LAST_FOUND_TIME", DateUtils.convertTimestampToString(Long.valueOf(String.valueOf(ip.get("LAST_FOUND_TIME"))), DateUtils.YYYY_MM_DD_HH24_MM_SS));
            ip.put("BYTES_TOTAL", Long.valueOf(String.valueOf(ip.get("CLIENT_BYTES_SUM"))) + Long.valueOf(String.valueOf(ip.get("SERVER_BYTES_SUM"))));
        }
        return resultList;
    }
}
