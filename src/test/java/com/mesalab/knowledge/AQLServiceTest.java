package com.mesalab.knowledge;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.mesalab.knowledge.entity.DSLObject;
import com.mesalab.knowledge.entity.Match;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.http.HttpHeaders;
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
public class AQLServiceTest {

    private static DSLObject.DSLQuery query = new DSLObject.DSLQuery();
    private static Parameters p = new Parameters();

    static {

        Match match = new Match();
        match.setFiledKey("FQDN_NAME");
//        match.setFiledValues("baidu");
        match.setType("substring");
        p.setMatches(Lists.newArrayList(match));

//        Range range = new Range();
//        range.setFiledKey("count");
//        range.setFiledValue("2");
//        range.setType("ge");
//        range.setOperator("or");
//        p.setRanges(Lists.newArrayList(range, range));

        p.setIntervals(Lists.newArrayList("2020-01-01 00:00:00/2020-01-02 00:00:00"));
        p.setLimit("10");

        query.setDatasource("FQDN");
        query.setQueryType("FQDN");
        query.setParameters(p);

    }

    @Autowired
    KnowledgeService aqlService;

    @Test
    public void aqlServiceTest() {
//        Object fqdn = aqlService.getFqdn(query);
//        System.out.println(fqdn);
        System.err.println(1);
    }

    @Autowired
    ArangoConfig arangoConfig;

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
    public void inf(){
        Header header = new BasicHeader(HttpHeaders.AUTHORIZATION, "jwt");
        HttpClientUtils.httpPost("http://192.168.40.182:8529/_db/select_iplearn_index/_open/auth","ccc",header);
    }
}
