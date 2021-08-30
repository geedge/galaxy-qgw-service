package com.mesalab.services.service.impl;

import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.common.utils.SpringContextUtil;
import com.mesalab.knowledge.common.utils.HttpConfig;
import com.mesalab.knowledge.enums.MatchEnum;
import com.mesalab.qgw.model.api.HBaseAPISource;
import com.mesalab.qgw.service.SystemService;
import com.mesalab.qgw.service.impl.HttpClientService;
import com.mesalab.services.common.dsl.ComDSLObject;
import com.mesalab.services.common.enums.EntityQueryType;
import com.mesalab.services.common.property.SqlPropertySourceFactory;
import com.mesalab.services.service.EntityService;
import com.zdjizhi.utils.StringUtil;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

/**
 * @author tiangaochao
 * @create: 2021-08-24
 */
@Service("entityService")
@PropertySource(value = "classpath:http-sql-template.sql", factory = SqlPropertySourceFactory.class)
public class EntityServiceImp implements EntityService, EnvironmentAware {
    private static final Log log = LogFactory.get();

    @Autowired
    private HttpClientService httpClientServiceTemp;
    @Autowired
    private HttpConfig httpConfigTemp;

    private static HttpClientService httpClientService;
    private static HttpConfig httpConfig;

    @PostConstruct
    public void init() {
        httpClientService = this.httpClientServiceTemp;
        httpConfig = this.httpConfigTemp;
    }

    @Value("${server.port}")
    private int serverPort;

    private Environment env;
    private static String localHostAddress;

    static {
        localHostAddress = NetUtil.getLocalhostStr();
    }

    @Override
    public BaseResult getEntityInfo(String param, ComDSLObject comDSLObject) {

        BaseResult baseResult;
        String fileSql;
        String sql;
        if (param.equalsIgnoreCase(EntityQueryType.ACTIVECLIENTIP.getType())) {
            fileSql = env.getProperty("ENTITY_ACTIVE_CLIENT_IP");
            sql = generateActiveClientIP(comDSLObject.getQuery(), fileSql);
            baseResult = getResult(sql);
        } else if (param.equalsIgnoreCase(EntityQueryType.TOPSERVERIP.getType())) {
            fileSql = env.getProperty("ENTITY_TOP_SERVER_IP");
            sql = generateTopServerIP(comDSLObject.getQuery(), fileSql);
            baseResult = getResult(sql);
        } else if (param.equalsIgnoreCase(EntityQueryType.TOPSNI.getType())) {
            fileSql = env.getProperty("ENTITY_TOP_SNI");
            sql = generateTopSNI(comDSLObject.getQuery(), fileSql);
            baseResult = getResult(sql);
        } else {
            baseResult = BaseResultGenerator.failure(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(), "No match queryType");
        }
        if (!baseResult.isSuccess()) {
            throw new BusinessException(baseResult.getStatus(), baseResult.getCode(), baseResult.getMessage(), null);
        }
        return baseResult;
    }

    public String generateActiveClientIP(ComDSLObject.Query query, String fileSql) {
        String timeInterval = getTimeInterval(query.getParameters());
        String matchDate = getMatchDate(query.getParameters());
        matchDate = StringUtil.isBlank(matchDate) ? "" : " AND " + matchDate;
        return String.format(fileSql, query.getDataSource(), timeInterval, matchDate, StringUtil.isBlank(query.getLimit()) ? 10000 : query.getLimit());
    }

    private String getMatchDate(ComDSLObject.Query.QueryBean query) {
        if (CollectionUtils.isEmpty(query.getMatch())) {
            return StringUtil.EMPTY;
        }
        StringBuffer matchDate = new StringBuffer();
        for (ComDSLObject.Query.FilterBean item : query.getMatch()) {
            if (MatchEnum.EXACTLY.getType().equals(item.getType()) && !CollectionUtils.isEmpty(item.getFieldValues())) {
                matchDate.append(item.getFieldKey()).append(" IN ('").append(String.join("','", item.getFieldValues())).append("')");
            }
        }
        return matchDate.toString();
    }

    private String getTimeInterval(ComDSLObject.Query.QueryBean parameters) {
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
            baseResult = BaseResultGenerator.success("ok", data, (Map) resultMap.get("statistics"));
        } else {
            baseResult = BaseResultGenerator.error(result.get("result"));
        }
        return baseResult;
    }

    public String generateTopServerIP(ComDSLObject.Query query, String fileSql) {
        String timeInterval = getTimeInterval(query.getParameters());
        String datasource = query.getDataSource();
        return String.format(fileSql, datasource, timeInterval, datasource, timeInterval, datasource, timeInterval, datasource, timeInterval);
    }

    public String generateTopSNI(ComDSLObject.Query query, String fileSql) {
        String timeInterval = getTimeInterval(query.getParameters());
        String datasource = query.getDataSource();
        return String.format(fileSql, datasource, timeInterval);
    }

    private Map<String, String> executeQuery(String sql) {
        try {
            sql = URLEncoder.encode(sql, "utf-8").replaceAll("\\+", "%20");
        } catch (UnsupportedEncodingException e) {
            log.error("sql Encode error: ", e);
        }

        String queryURL = URLUtil.normalize(localHostAddress + ":" + serverPort + "/?option=long-term&resultId=" + DigestUtil.md5Hex(sql) + "&query=");
        int socketTimeOut = httpConfig.getCkLongTermAccountSocketTimeOut();
        return httpClientService.httpGet(queryURL + sql, socketTimeOut);
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.env = environment;
    }
}
