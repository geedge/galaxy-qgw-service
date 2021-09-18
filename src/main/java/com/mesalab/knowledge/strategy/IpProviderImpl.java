package com.mesalab.knowledge.strategy;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesalab.knowledge.entity.DSLObject;
import com.mesalab.knowledge.entity.Parameters;
import com.mesalab.knowledge.entity.Sort;
import com.zdjizhi.utils.DateUtils;
import com.zdjizhi.utils.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
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
@Component("IP_VIEW")
public class IpProviderImpl implements QueryProvider {

    @Value("${arango.maxrows}")
    private String limit;//查询最大条数
    @Autowired
    BaseQueryProvider baseQueryProvider;

    @Override
    public Object query(DSLObject.DSLQuery dslQeruy) {
        Parameters parameters = dslQeruy.getParameters();
        StringBuffer aqlsb = new StringBuffer();
        String sortAql = "";
        //需要条件  时间，limit
        if(!ObjectUtils.isEmpty(parameters)){
            limit = ObjectUtils.isEmpty(parameters.getLimit()) ? limit : parameters.getLimit();
            List<Sort> sorts = parameters.getSort();

            if (Objects.nonNull(sorts)) {
                sortAql = sorts.stream().map(x -> {
                    if ("BYTES_TOTAL".equals(x.getFieldKey())) {
                        return "(doc.CLIENT_BYTES_SUM+doc.SERVER_BYTES_SUM) " + x.getType();
                    } else if ("LAST_FOUND_TIME".equals(x.getFieldKey())) {
                        return "doc.LAST_FOUND_TIME " + x.getType();
                    }
                    return null;
                }).filter(x -> StringUtil.isNotBlank(x)).collect(Collectors.joining(",", " ", " "));
            }
        }
        aqlsb.append("for doc in IP ");
        aqlsb.append(" filter doc.COMMON_LINK_INFO != '' and doc.FIRST_FOUND_TIME > 0 ");
        aqlsb.append(StringUtil.isBlank(sortAql) ? "" : " sort " + sortAql);
        aqlsb.append(" limit ").append(limit);
        aqlsb.append(" return distinct doc");
        //1.查询
        Object result = baseQueryProvider.queryArango(aqlsb.toString());
        //2.解析结果
        List<Map> resultList = new ObjectMapper().convertValue(result, new TypeReference<List<Map>>() {
        });
        return parseResult(resultList);

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
