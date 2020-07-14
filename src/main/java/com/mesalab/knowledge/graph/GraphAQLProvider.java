package com.mesalab.knowledge.graph;

import java.util.Map;

import com.mesalab.knowledge.common.config.ArangoConfig;
import com.mesalab.knowledge.entity.DSLObject;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableMap;
import com.mesalab.knowledge.entity.QLConditions;
import com.zdjizhi.utils.JsonMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * @description: provider aql
 * @author: zhq
 * @create: 2020-07-02
 **/
@Slf4j
@Component
public class GraphAQLProvider implements QLProvider {


    /**
     * 查询样例
     * for doc in FQDN
     * filter doc._key like $fqdn
     * limit $limit
     * return doc
     */
    public Map<String, Object> buildAQL(QLConditions aqlSegment, DatasourceRouteEnum datasourceEnum) {

        ImmutableMap<String, Object> map = ImmutableMap.of(
                "query", datasourceEnum.getAqlSchema(),
                "bindVars", ImmutableMap.of("@collection", aqlSegment.getDatasource(),"filter", aqlSegment.getQlFilter()));
        log.info("查询的AQL:{}",JsonMapper.toJsonString(map));
        return map;
    }

    @Override
    public Object query(DSLObject.DSLQuery dslQeruy, ArangoConfig arangoConfig) {
        return null;
    }
}
