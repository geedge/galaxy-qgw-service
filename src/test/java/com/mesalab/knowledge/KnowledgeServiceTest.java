package com.mesalab.knowledge;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.knowledge.entity.*;
import com.mesalab.knowledge.service.KnowledgeService;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @description: for aql service test
 * @author: zhq
 * @create: 2020-07-06
 **/
@EnableAutoConfiguration
public class KnowledgeServiceTest extends GalaxyQGWApplicationTests {
    private static final Log log = LogFactory.get();

    private static DSLObject.DSLQuery query = new DSLObject.DSLQuery();
    private static Parameters p = new Parameters();
    private static Match match = new Match();
    private static Range range = new Range();
    private static Range range2 = new Range();


    static {

        match.setFieldKey("FQDN_NAME");
        match.setFieldValues(ImmutableList.of("", ""));
        match.setType("substring");
        p.setMatch(Lists.newArrayList(match, match, match));

        range.setFieldKey("PROTOCOL");
        range.setFieldValues(ImmutableList.of("HTTP"));
        range.setType("ge");

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
    KnowledgeService knowledgeService;

    @Test
    public void queryIpelarning() {
        DSLObject dslObject = new DSLObject();
        dslObject.setQuery(query);
        Object query = knowledgeService.query(dslObject);
        Stream.of(query).forEach(x -> log.info(x.toString()));
    }

    @Test
    public void matchTest() {
        DSLObject dslObject = new DSLObject();
        dslObject.setQuery(query);
        Object query = knowledgeService.query(dslObject);
        log.info(query.toString());
    }

    @Test
    public void sortTest() {
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
        Assert.assertEquals(collect, " sort (doc.CLIENT_BYTES_SUM+doc.SERVER_BYTES_SUM) desc,doc.LAST_FOUND_TIME asc ");
    }
}
