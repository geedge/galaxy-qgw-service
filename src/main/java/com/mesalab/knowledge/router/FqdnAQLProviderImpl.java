package com.mesalab.knowledge.router;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;
import com.mesalab.knowledge.common.config.ArangoConfig;
import com.mesalab.knowledge.common.utils.HttpClientUtils;
import com.mesalab.knowledge.entity.Match;
import com.mesalab.knowledge.parse.CombinedSegment;
import com.mesalab.knowledge.entity.DSLObject;
import com.mesalab.knowledge.entity.Parameters;
import com.mesalab.knowledge.entity.QLConditions;
import com.mesalab.knowledge.graph.DatasourceRouteEnum;
import com.mesalab.knowledge.graph.QLProvider;
import com.zdjizhi.utils.JsonMapper;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.springframework.http.HttpHeaders;

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
            QLConditions condition = CombinedSegment.getCondition(parameters);
            //0.校验
            //1.处理条件 2.需要的条件：fqdn_name 多个, depth, filter:last_found_time, dist_cip, http,tls,dns, limit
            Map bindVars = ImmutableMap.of();

            //1.构建查询语句 query ,bindVars
            StringBuffer fqndsb = new StringBuffer();

            List<Match> matches = parameters.getMatches();
            matches.stream().filter(m -> m != null && "FQDN_NAME".equals(m.getFiledKey())).forEach(m -> {
                fqndsb.append(m.getFiledValues().stream().filter(mv -> mv != null && !mv.isEmpty()).collect(joining(" or doc._key like ", " doc._key like ", "")));
            });

//            Map map = graphAQLProvider.buildAQL(condition, DatasourceRouteEnum.valueOf(datasource));
//            Header header = new BasicHeader(HttpHeaders.AUTHORIZATION, arangoConfig.getJwt());
//            String res = HttpClientUtils.httpPost(Joiner.on("").join(arangoConfig.getServer(), "/_db/", arangoConfig.getDatabase(), "/_api/cursor"), JsonMapper.toJsonString(map), header);
//            //2.解析结果
//            System.err.println(res);
        }

        return null;

    }

}
