package com.mesalab.knowledge;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.google.common.collect.ImmutableList;
import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.knowledge.entity.DSLObject;
import com.mesalab.knowledge.entity.Match;
import com.mesalab.knowledge.entity.Range;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.util.Assert;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.mesalab.knowledge.common.config.ArangoConfig;
import com.mesalab.knowledge.common.utils.HttpClientUtils;
import com.mesalab.knowledge.common.utils.JwtCache;
import com.mesalab.knowledge.entity.Parameters;
import com.mesalab.knowledge.service.KnowledgeService;
import com.zdjizhi.utils.JsonMapper;
import com.zdjizhi.utils.StringUtil;

/**
 * @description: for aql service test
 * @author: zhq
 * @create: 2020-07-06
 **/
@EnableAutoConfiguration
public class AQLServiceTest extends GalaxyQGWApplicationTests {

    private static DSLObject.DSLQuery query = new DSLObject.DSLQuery();
    private static Parameters p = new Parameters();

    static {

        Match match = new Match();
        match.setFiledKey("FQDN_NAME");
        match.setFiledValues(ImmutableList.of(""));
        match.setType("substring");
        p.setMatches(Lists.newArrayList(match));

        Range range = new Range();
        range.setFiledKey("PROTOCOL");
        range.setFiledValues(ImmutableList.of("HTTP"));
        range.setType("ge");
        Range range2 = new Range();
        range2.setFiledKey("DEPTH");
        range2.setFiledValues(ImmutableList.of("1..1"));
        range2.setType("ge");
//        range.setOperator("or");
        p.setRanges(Lists.newArrayList(range, range2));

        p.setIntervals(Lists.newArrayList("2020-01-01 00:00:00/2020-07-16 15:59:52"));
        p.setLimit("1");

        query.setDatasource("IP_LEARNING_VIEW");
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
    public void query() {
        Object query = knowledgeService.query(AQLServiceTest.query);
        Assert.notNull(query, "结果为空");
    }
}
