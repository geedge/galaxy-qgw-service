package com.mesalab.entity.service.imp;

import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.common.utils.SpringContextUtil;
import com.mesalab.entity.entity.DSLObject;
import com.mesalab.entity.enums.EntityQueryTypeEnum;
import com.mesalab.entity.service.EntityService;
import com.mesalab.knowledge.common.utils.HttpConfig;
import com.mesalab.knowledge.enums.MatchEnum;
import com.mesalab.network.property.SqlPropertySourceFactory;
import com.mesalab.qgw.model.api.ClickHouseHttpSource;
import com.mesalab.qgw.service.impl.HttpClientService;
import com.zdjizhi.utils.StringUtil;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

/**
 * @author tiangaochao
 * @date 2021/8/30 15:16 下午
 * */
@Service("entityService")
@PropertySource(value = "classpath:http-sql-template.sql", factory = SqlPropertySourceFactory.class)
public class EntityServiceImp implements EntityService, EnvironmentAware {
    private static final Log log = LogFactory.get();
    private ClickHouseHttpSource clickHouseHttpSource = (ClickHouseHttpSource) SpringContextUtil.getBean("clickHouseHttpSource");
    @Autowired
    HttpClientService httpClientService;
    @Autowired
    HttpConfig httpConfig;
    @Value("${server.port}")
    private int serverPort;

    private Environment env;
    private static String localHostAddress;

    static {
        localHostAddress = NetUtil.getLocalhostStr();
    }

    @Override
    public BaseResult getDslInfo(String param, DSLObject dslObject) {

        BaseResult baseResult;
        String fileSql;
        String sql;
        if (param.equalsIgnoreCase(EntityQueryTypeEnum.ACTIVECLIENTIP.getType())) {
            fileSql = env.getProperty("ENTITY_ACTIVECLIENTIP");
            sql = generateClientSql(dslObject.getQuery(), fileSql);
            baseResult = getResult(sql);
        } else if (param.equalsIgnoreCase(EntityQueryTypeEnum.TOPSERVERIP.getType())) {
            fileSql = env.getProperty("ENTITY_TOPSERVERIP");
            sql = generateServerSql(dslObject.getQuery(), fileSql);
            baseResult = getResult(sql);
        } else if (param.equalsIgnoreCase(EntityQueryTypeEnum.TOPSNI.getType())) {
            fileSql = env.getProperty("ENTITY_TOPSNI");
            sql = generateSniSql(dslObject.getQuery(), fileSql);
            baseResult = getResult(sql);
        } else {
            baseResult = BaseResultGenerator.failure(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(), "No match queryType");
        }
        if (!baseResult.isSuccess()) {
            throw new BusinessException(baseResult.getStatus(), baseResult.getCode(), baseResult.getMessage(), null);
        }
        return baseResult;
    }

    public String generateClientSql(DSLObject.Query query, String fileSql) {
        String timeInterval = getTimeInterval(query.getParameters());
        String matchDate = getMatchDate(query.getParameters());
        matchDate = StringUtil.isBlank(matchDate) ? "" : " AND " + matchDate;
        String sql = String.format(fileSql, query.getDataSource(), timeInterval, matchDate, query.getLimit());
        return sql;
    }

    private String getMatchDate(DSLObject.Query.QueryBean query) {
        if (CollectionUtils.isEmpty(query.getMatch())) {
            return StringUtil.EMPTY;
        }
        StringBuffer MatchDate = new StringBuffer();
        for (DSLObject.Query.MatchBean item : query.getMatch()) {
            if (MatchEnum.EXACTLY.getType().equals(item.getType()) && !CollectionUtils.isEmpty(item.getFieldValues())) {
                MatchDate.append(item.getFieldKey()).append(" IN ('").append(String.join("','", item.getFieldValues())).append("')");
            }
        }
        return MatchDate.toString();
    }

    private String getTimeInterval(DSLObject.Query.QueryBean parameters) {
        if (CollectionUtils.isEmpty(parameters.getIntervals())) {
            return StringUtil.EMPTY;
        }
        StringBuffer timeInterval = new StringBuffer();
        String[] intervals = getIntervals(parameters.getIntervals());
        timeInterval.append("common_recv_time >= toDateTime('").append(intervals[0]).append("') AND common_recv_time< toDateTime('").append(intervals[1]).append("')");
        return timeInterval.toString();
    }

    private String[] getIntervals(List<String> intervals) {
        return intervals.get(0).split("/");
    }

    public BaseResult getResult(String sql) {
        BaseResult baseResult;
        Map<String, String> result = executeQuery(sql);
        if (String.valueOf(HttpStatus.SC_OK).equals(result.get("status"))) {
            Map resultMap = (Map) JsonMapper.fromJsonString(result.get("result"), Map.class);
            List<Map> data = (List<Map>) resultMap.get("data");
            List<Map> meta = (List<Map>) resultMap.get("meta");
            baseResult = BaseResultGenerator.success("ok", data, meta, (Map) resultMap.get("statistics"));
        } else {
            baseResult = BaseResultGenerator.error(result.get("result"));
        }
        return baseResult;
    }

    public String generateServerSql(DSLObject.Query query, String fileSql) {
        String timeInterval = getTimeInterval(query.getParameters());
        String datasource = query.getDataSource();
        String sql = String.format(fileSql, datasource, timeInterval, datasource, timeInterval, datasource, timeInterval, datasource, timeInterval);
        return sql;
    }

    public String generateSniSql(DSLObject.Query query, String fileSql) {
        String timeInterval = getTimeInterval(query.getParameters());
        String datasource = query.getDataSource();
        String sql = String.format(fileSql, datasource, timeInterval);
        return sql;
    }

    private Map<String, String> executeQuery(String sql) {
        try {
            sql = URLEncoder.encode(sql, "utf-8").replaceAll("\\+", "%20");
        } catch (UnsupportedEncodingException e) {
            log.error("sql Encode error: ", e);
        }

        String queryURL = URLUtil.normalize(localHostAddress + ":" + serverPort + "/?option=long-term&query=");
        int socketTimeOut = httpConfig.getCkLongTermAccountSocketTimeOut();
        return httpClientService.httpGet(queryURL + sql, socketTimeOut);
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.env = environment;
    }
}
