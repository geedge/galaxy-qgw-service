package com.mesalab.services.service.impl;

import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.beust.jcommander.internal.Sets;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.QueryFormatEnum;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.knowledge.common.utils.HttpConfig;
import com.mesalab.knowledge.enums.MatchEnum;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.EntityConfigSource;
import com.mesalab.qgw.service.impl.ApiServiceImpl;
import com.mesalab.qgw.service.impl.HttpClientService;
import com.mesalab.services.common.dsl.ComDSLObject;
import com.mesalab.services.common.dsl.ComDSLParse;
import com.mesalab.services.common.enums.EntityQueryType;
import com.mesalab.services.common.property.SqlPropertySourceFactory;
import com.mesalab.services.service.EntityService;
import com.zdjizhi.utils.DateUtils;
import com.zdjizhi.utils.StringUtil;
import com.zdjizhi.utils.TimeConstants;
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
import java.util.*;

/**
 * @author tiangaochao
 * @create: 2021-08-24
 */
@Service("entityService")
@PropertySource(value = "classpath:http-sql-template.sql", factory = SqlPropertySourceFactory.class)
public class EntityServiceImp implements EntityService, EnvironmentAware {
    private static final Log log = LogFactory.get();

    @Autowired
    private ApiServiceImpl apiService;
    @Autowired
    private HttpClientService httpClientServiceTemp;
    @Autowired
    private HttpConfig httpConfigTemp;
    @Autowired
    private EntityConfigSource entityConfigSourceTemp;

    private static HttpClientService httpClientService;
    private static HttpConfig httpConfig;
    private static EntityConfigSource entityConfigSource;

    @PostConstruct
    public void init() {
        httpClientService = this.httpClientServiceTemp;
        httpConfig = this.httpConfigTemp;
        entityConfigSource = this.entityConfigSourceTemp;
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
            setDefaultValue(comDSLObject.getQuery());
            baseResult = getActiveClientIP(comDSLObject.getQuery());
        } else if (param.equalsIgnoreCase(EntityQueryType.TOPSERVERIP.getType())) {
            baseResult = getResultOfServerIP(comDSLObject.getQuery());
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

    private BaseResult getActiveClientIP(ComDSLObject.Query query) {
        BaseResult baseResult;
        ComDSLObject.Query.FilterBean filterBean = query.getParameters().getMatch().get(0);
        if (MatchEnum.EXACTLY.getType().equalsIgnoreCase(String.valueOf(filterBean.getType()))) {
            baseResult = apiService.executeQuery(queryBuild(query));
            if (isValid(baseResult, getIntervals(query.getParameters().getIntervals()))) {
                baseResult = formatResult(baseResult, query.getLimit());
            } else {
                baseResult = getResult(generateActiveClientIP(query, env.getProperty("ENTITY_ACTIVE_CLIENT_IP"), query.getLimit()));
            }
        } else {
            throw new BusinessException(HttpStatus.SC_BAD_REQUEST, ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                    "ComDSLObject.query.parameters.match.type is invalid ( or is null)");
        }
        return baseResult;
    }

    private void setDefaultValue(ComDSLObject.Query query) {
        if (StringUtil.isEmpty(query.getParameters().getIntervals())) {
            Date now = new Date();
            Date someMinute = DateUtils.getSomeMinute(now, -30);
            String end = DateUtils.getFormatDate(now, DateUtils.YYYY_MM_DD_HH24_MM_SS);
            String start = DateUtils.getFormatDate(someMinute, DateUtils.YYYY_MM_DD_HH24_MM_SS);
            List<String> list = Lists.newArrayList(start.concat("/").concat(end));
            query.getParameters().setIntervals(list);
        }
        query.setLimit(StringUtil.isEmpty(query.getLimit()) || Integer.parseInt(query.getLimit()) > entityConfigSource.getActiveClientIP() ? String.valueOf(entityConfigSource.getActiveClientIP()) : query.getLimit());
    }

    public boolean isValid(BaseResult baseResult, String[] intervals) {
        if (StringUtil.isEmpty(baseResult.getData())) {
            return false;
        }
        List<Map> data = (List<Map>) baseResult.getData();
        long updateTime = (long) (data.get(0).get("last_update_time"));
        long paramStartTime = DateUtils.convertStringToTimestamp(intervals[0], TimeConstants.YYYY_MM_DD_HH24_MM_SS);
        long paramEndTime = DateUtils.convertStringToTimestamp(intervals[1], TimeConstants.YYYY_MM_DD_HH24_MM_SS);
        if (paramStartTime >= updateTime - 30 * 60 && paramEndTime <= updateTime + 30 * 60) {
            return true;
        } else {
            return false;
        }
    }

    private BaseResult formatResult(BaseResult baseResult, String limit) {
        if (String.valueOf(HttpStatus.SC_OK).equals(String.valueOf(baseResult.getStatus()))) {
            List<Object> alterationResultList = new ArrayList<>();
            List<Map> resultList = new ObjectMapper().convertValue(baseResult.getData(), new TypeReference<List<Map>>() {
            });
            resultList.forEach(r -> {
                if (ObjectUtil.isNotEmpty(r)) {
                    List<Object> list = (List<Object>) JsonMapper.fromJsonList(String.valueOf(r.get("client_ip")), Object.class);
                    alterationResultList.addAll(list);
                }
            });
            alterationResultList.sort(new Comparator<Object>() {
                @Override
                public int compare(Object o1, Object o2) {
                    Integer sessionsO1 = Integer.parseInt(String.valueOf(((Map) o1).get("sessions")));
                    Integer sessionsO2 = Integer.parseInt(String.valueOf(((Map) o2).get("sessions")));
                    return sessionsO2.compareTo(sessionsO1);
                }
            });

            List<Object> dataResult = new ArrayList<>();
            for (Object o : alterationResultList) {
                if (dataResult.size() >= Integer.parseInt(limit)) {
                    break;
                }
                Map clientIp = new HashMap();
                clientIp.put("client_ip", ((Map) o).get("common_client_ip"));
                dataResult.add(clientIp);
            }
            baseResult.getStatistics().put("result_rows", dataResult.size());
            baseResult.getStatistics().put("result_size", JsonMapper.toJsonString(dataResult).getBytes().length);
            return BaseResultGenerator.generate(baseResult.getStatus(), baseResult.getCode(), baseResult.getMessage(), dataResult, baseResult.getMeta(), baseResult.getStatistics(), QueryFormatEnum.JSON.getValue());
        } else {
            baseResult = BaseResultGenerator.error(baseResult.getStatus(), baseResult.getMessage());
        }
        return baseResult;
    }

    private ApiParam queryBuild(ComDSLObject.Query query) {
        String fieldWhere = ComDSLParse.parseMath(query, "ROWKEY");
        String sql = String.format(Objects.requireNonNull(env.getProperty("ENTITY_CACHE_ACTIVE_CLIENT_IP")), fieldWhere);
        log.info("real time relation active_client_ip id query sql: {}", sql);
        return ApiParam.builder().option("real-time").query(sql).build();
    }

    private BaseResult getResultOfServerIP(ComDSLObject.Query query) {
        String timeInterval = getTimeInterval(query.getParameters(), -24 * 60);
        String datasource = query.getDataSource();

        String tcpSessionSql = String.format(Objects.requireNonNull(env.getProperty("ENTITY_TCP_SESSION")), datasource, timeInterval,
                StringUtil.isBlank(query.getLimit()) || Integer.parseInt(query.getLimit()) > entityConfigSource.getTopServerIPByTCPSession() ? entityConfigSource.getTopServerIPByTCPSession() : query.getLimit());
        Map<String, String> tcpSessionResult = executeQuery(tcpSessionSql);

        String tcpClientIpsSql = String.format(Objects.requireNonNull(env.getProperty("ENTITY_TCP_UNIQ_CLIENT_IPS")), datasource, timeInterval,
                StringUtil.isBlank(query.getLimit()) || Integer.parseInt(query.getLimit()) > entityConfigSource.getTopServerIPByTCPUniqClientIP() ? entityConfigSource.getTopServerIPByTCPUniqClientIP() : query.getLimit());
        Map<String, String> tcpClientIpResult = executeQuery(tcpClientIpsSql);

        String udpSessionSql = String.format(Objects.requireNonNull(env.getProperty("ENTITY_UDP_SESSION")), datasource, timeInterval,
                StringUtil.isBlank(query.getLimit()) || Integer.parseInt(query.getLimit()) > entityConfigSource.getTopServerIPByUDPBySession() ? entityConfigSource.getTopServerIPByUDPBySession() : query.getLimit());
        Map<String, String> udpSessionResult = executeQuery(udpSessionSql);

        String udpClientIpsSql = String.format(Objects.requireNonNull(env.getProperty("ENTITY_UDP_UNIQ_CLIENT_IPS")), datasource, timeInterval,
                StringUtil.isBlank(query.getLimit()) || Integer.parseInt(query.getLimit()) > entityConfigSource.getTopServerIPByUDPUniqClientIP() ? entityConfigSource.getTopServerIPByUDPUniqClientIP() : query.getLimit());
        Map<String, String> udpClientIpsResult = executeQuery(udpClientIpsSql);

        if (!String.valueOf(HttpStatus.SC_OK).equals(tcpSessionResult.get("status"))
                || !String.valueOf(HttpStatus.SC_OK).equals(tcpClientIpResult.get("status"))
                || !String.valueOf(HttpStatus.SC_OK).equals(udpSessionResult.get("status"))
                || !String.valueOf(HttpStatus.SC_OK).equals(udpClientIpsResult.get("status"))) {
            return BaseResultGenerator.error(
                    "tcp by session result:" + tcpSessionResult
                            + "tcp by client ips result:" + tcpClientIpResult
                            + "udp by session result:" + udpSessionResult
                            + "udp by client ips result:" + udpClientIpsResult
            );
        }

        HashSet<Map<String, String>> hashSet = new HashSet<>();
        getData(udpSessionResult).forEach(hashSet::add);
        getData(udpClientIpsResult).forEach(hashSet::add);
        getData(tcpSessionResult).forEach(hashSet::add);
        getData(tcpClientIpResult).forEach(hashSet::add);

        Set<Map<String, String>> data = Sets.newHashSet();
        if (StringUtil.isNotEmpty(query.getLimit()) && hashSet.size() > Integer.parseInt(query.getLimit())) {
            for (Map<String, String> item : hashSet) {
                if (data.size() >= Integer.parseInt(query.getLimit())) {
                    break;
                }
                data.add(item);
            }
        } else {
            data = hashSet;
        }
        Map<String, Object> statistics = Maps.newHashMap();
        Map tcpSessionResultMap = (Map) JsonMapper.fromJsonString(tcpSessionResult.get("result"), Map.class);
        Map tcpSessionStatistics = (Map) tcpSessionResultMap.get("statistics");
        Map tcpClientIpResultMap = (Map) JsonMapper.fromJsonString(tcpClientIpResult.get("result"), Map.class);
        Map tcpClientIpStatistics = (Map) tcpClientIpResultMap.get("statistics");
        Map udpSessionResultMap = (Map) JsonMapper.fromJsonString(udpSessionResult.get("result"), Map.class);
        Map udpSessionStatistics = (Map) udpSessionResultMap.get("statistics");
        Map udpClientIpResultMap = (Map) JsonMapper.fromJsonString(udpClientIpsResult.get("result"), Map.class);
        Map udpClientIpStatistics = (Map) udpClientIpResultMap.get("statistics");
        for (Object key : tcpSessionStatistics.keySet()) {
            if ("result_size".equals(key.toString())) {
                statistics.put(key.toString(), JsonMapper.toJsonString(data).getBytes().length);
            } else if ("result_rows".equals(key.toString())) {
                statistics.put(key.toString(), data.size());
            } else {
                statistics.put(String.valueOf(key), Long.parseLong(tcpSessionStatistics.get(key).toString())
                        + Long.parseLong(tcpClientIpStatistics.get(key).toString())
                        + Long.parseLong(udpSessionStatistics.get(key).toString())
                        + Long.parseLong(udpClientIpStatistics.get(key).toString()));
            }
        }
        return BaseResultGenerator.success("ok", data, statistics);
    }

    public List<Map> getData(Map<String, String> result) {
        Map resultMap = (Map) JsonMapper.fromJsonString(result.get("result"), Map.class);
        return (List<Map>) resultMap.get("data");
    }

    public String generateActiveClientIP(ComDSLObject.Query query, String fileSql, String limit) {
        String timeInterval = getTimeInterval(query.getParameters(), -30);
        String matchDate = getMatchDate(query.getParameters());
        matchDate = StringUtil.isBlank(matchDate) ? "" : " AND " + matchDate;
        return String.format(fileSql, query.getDataSource(), timeInterval, matchDate, limit);
    }

    private String getMatchDate(ComDSLObject.Query.QueryBean query) {
        if (CollectionUtils.isEmpty(query.getMatch())) {
            return StringUtil.EMPTY;
        }
        StringBuilder matchDate = new StringBuilder();
        for (ComDSLObject.Query.FilterBean item : query.getMatch()) {
            if (MatchEnum.EXACTLY.getType().equals(item.getType()) && !CollectionUtils.isEmpty(item.getFieldValues())) {
                matchDate.append(item.getFieldKey()).append(" IN ('").append(String.join("','", item.getFieldValues())).append("')");
            }
        }
        return matchDate.toString();
    }

    private String getTimeInterval(ComDSLObject.Query.QueryBean parameters, int defaultInterval) {
        if (StringUtil.isEmpty(parameters.getIntervals())) {
            Date now = new Date();
            Date someMinute = DateUtils.getSomeMinute(now, defaultInterval);
            String end = DateUtils.getFormatDate(now, DateUtils.YYYY_MM_DD_HH24_MM_SS);
            String start = DateUtils.getFormatDate(someMinute, DateUtils.YYYY_MM_DD_HH24_MM_SS);
            List<String> list = Lists.newArrayList(start.concat("/").concat(end));
            parameters.setIntervals(list);
        }
        StringBuilder timeInterval = new StringBuilder();
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

    public String generateTopSNI(ComDSLObject.Query query, String fileSql) {
        String timeInterval = getTimeInterval(query.getParameters(), -24 * 60);
        String datasource = query.getDataSource();
        return String.format(fileSql, datasource, timeInterval, StringUtil.isBlank(query.getLimit()) || Integer.parseInt(query.getLimit()) > entityConfigSource.getTopSNI() ? entityConfigSource.getTopSNI() : query.getLimit());
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
