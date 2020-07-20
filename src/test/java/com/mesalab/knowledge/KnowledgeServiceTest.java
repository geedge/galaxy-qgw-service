package com.mesalab.knowledge;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableList;
import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.knowledge.entity.*;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.mesalab.knowledge.common.config.ArangoConfig;
import com.mesalab.knowledge.common.utils.HttpClientUtils;
import com.mesalab.knowledge.common.utils.JwtCache;
import com.mesalab.knowledge.service.KnowledgeService;
import com.zdjizhi.utils.JsonMapper;
import com.zdjizhi.utils.StringUtil;

/**
 * @description: for aql service test
 * @author: zhq
 * @create: 2020-07-06
 **/
@EnableAutoConfiguration
public class KnowledgeServiceTest extends GalaxyQGWApplicationTests {

    private static DSLObject.DSLQuery query = new DSLObject.DSLQuery();
    private static Parameters p = new Parameters();

    static {

        Match match = new Match();
        match.setFieldKey("FQDN_NAME");
        match.setFieldValues(ImmutableList.of(""));
        match.setType("substring");
        p.setMatch(Lists.newArrayList(match));

        Range range = new Range();
        range.setFieldKey("PROTOCOL");
        range.setFieldValues(ImmutableList.of("HTTP"));
        range.setType("ge");
        Range range2 = new Range();
        range2.setFieldKey("DEPTH");
        range2.setFieldValues(ImmutableList.of("1..1"));
        range2.setType("ge");
//        range.setOperator("or");
        p.setRange(Lists.newArrayList(range, range2));

        p.setIntervals(Lists.newArrayList("2020-01-01 00:00:00/2020-07-16 15:59:52"));
        p.setLimit("1");

        query.setDataSource("IP_LEARNING_VIEW");
        query.setQueryType("FQDN");
        query.setParameters(p);

    }

    @Autowired
    ArangoConfig arangoConfig;

    @Autowired
    KnowledgeService knowledgeService;

    @Test
    public void getJwt() {
        Object jwt = JwtCache.get("jwt");
        if (StringUtil.isEmpty(jwt)) {
            Map<String, String> params = new HashMap<>();
            params.put("username", arangoConfig.getUsername());
            params.put("password", arangoConfig.getPassword());
            String res = HttpClientUtils.httpPost(Joiner.on("").join(arangoConfig.getServer(), "/_db/", arangoConfig.getDatabase(), "/_open/auth"), JsonMapper.toJsonString(params));
            Map resMap = (Map) JsonMapper.fromJsonString(res, Map.class);
            jwt = resMap.get("jwt") != null ? "bearer " + resMap.get("jwt") : null;
            JwtCache.put("jwt", jwt);
        }
        Objects.requireNonNull(jwt);
    }


    @Test
    public void queryIpelarning() {
        DSLObject dslObject = new DSLObject();
        dslObject.setQuery(query);
        Object query = knowledgeService.query(dslObject);
        Stream.of(query).forEach(x-> System.out.println(x));
    }

    @Test
    public void sortTest(){
        List<Sort> list = Lists.newArrayList();
        Sort sort = new Sort();
        sort.setFieldKey("BYTES_TOTAL");
        sort.setType("desc");
        Sort sort2 = new Sort();
        sort2.setFieldKey("LAST_FOUND_TIME");
        sort2.setType("asc");
        list.add(sort);
        list.add(sort2);

        String collect = list.stream().map(x -> {
            if ("BYTES_TOTAL".equals(x.getFieldKey())) {
                return "(doc.CLIENT_BYTES_SUM+doc.SERVER_BYTES_SUM) " + x.getType();
            } else if ("LAST_FOUND_TIME".equals(x.getFieldKey())) {
                return "doc.LAST_FOUND_TIME " + x.getType();
            }
            return "";
        }).collect(Collectors.joining(",", " sort ", " "));
        Assert.assertEquals(collect," sort (doc.CLIENT_BYTES_SUM+doc.SERVER_BYTES_SUM) desc,doc.LAST_FOUND_TIME asc ");
    }
}
