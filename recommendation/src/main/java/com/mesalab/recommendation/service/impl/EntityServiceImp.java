package com.mesalab.recommendation.service.impl;

import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.*;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.beust.jcommander.internal.Sets;
import com.google.common.base.Splitter;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.InternetDomainName;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.MatchEnum;
import com.mesalab.common.enums.QueryFormatEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.qgw.model.api.HttpConfig;
import com.mesalab.qgw.model.api.EntityConfigSource;
import com.mesalab.qgw.service.MetadataService;
import com.mesalab.qgw.service.impl.ApiServiceImpl;
import com.mesalab.qgw.utils.HttpClientService;
import com.mesalab.common.entity.ComDSLObject;
import com.mesalab.common.entity.ComDSLParse;
import com.mesalab.qgw.enums.EntityQueryType;
import com.mesalab.qgw.common.property.SqlPropertySourceFactory;
import com.mesalab.recommendation.service.EntityService;
import com.zdjizhi.utils.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
    @Autowired
    private MetadataService metadataService;

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
        String sql;
        if (param.equalsIgnoreCase(EntityQueryType.ACTIVECLIENTIP.getType())) {
            setDefaultValue(comDSLObject.getQuery());
            baseResult = getActiveClientIP(comDSLObject.getQuery());
        } else if (param.equalsIgnoreCase(EntityQueryType.TOPSERVERIP.getType())) {
            baseResult = getResultOfServerIP(comDSLObject.getQuery());
        } else if (param.equalsIgnoreCase(EntityQueryType.TOPSNI.getType())) {
            sql = generateTopSNI(comDSLObject.getQuery(), env.getProperty("ENTITY_TOP_SNI"));
            baseResult = query(sql);

            String totalSQL = generateSNITotal(comDSLObject.getQuery(), env.getProperty("ENTITY_SNI_TOTAL"));
            BaseResult baseResultTotal = query(totalSQL);
            int limit = StringUtil.isBlank(comDSLObject.getQuery().getLimit()) ? entityConfigSource.getTopSNIDefaultSize() : Integer.parseInt(comDSLObject.getQuery().getLimit());
            baseResult = build(limit, baseResult, baseResultTotal);
        } else {
            baseResult = BaseResultGenerator.failure(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(), "No match queryType");
        }
        if (!baseResult.isSuccess()) {
            BaseResult result = JsonMapper.fromJsonString(baseResult.getMessage(), BaseResult.class);
            throw new QGWBusinessException(baseResult.getStatus(), baseResult.getCode(), result.getMessage());
        }
        return baseResult;
    }

    private BaseResult build(int limit, BaseResult baseResult, BaseResult baseResultTotal) {
        if (!baseResult.isSuccess() || !baseResultTotal.isSuccess()) {
            return baseResult.isSuccess() ? baseResultTotal : baseResult;
        }
        Stopwatch watch = Stopwatch.createStarted();
        List<Map<String, Object>> data = (List<Map<String, Object>>) baseResult.getData();
        List<Map<String, Object>> total = (List<Map<String, Object>>) baseResultTotal.getData();
        long totalBytes = StringUtil.isEmpty(total.get(0).get("bytes")) ? 0 : Long.parseLong(total.get(0).get("bytes").toString());
        long totalSessions = StringUtil.isEmpty(total.get(0).get("sessions")) ? 0 : Long.parseLong(total.get(0).get("sessions").toString());
        List<Map<String, Object>> result = mergeSNI(data);

        //sort
        result.sort((o1, o2) -> {
            long numThis = StringUtil.isEmpty(o2.get("sessions")) ? 0 : Long.parseLong(o2.get("sessions").toString());
            long numParam = StringUtil.isEmpty(o1.get("sessions")) ? 0 : Long.parseLong(o1.get("sessions").toString());
            if (numThis > numParam) {
                return 1;
            } else if (numThis < numParam) {
                return -1;
            } else {
                return 0;
            }
        });

        result = result.size() > limit ? result.subList(0, limit) : result;

        long sessionsMerge = 0;
        long bytesMerge = 0;
        long countCDN = 0;

        for (Map<String, Object> item : result) {
            bytesMerge += StringUtil.isEmpty(item.get("bytes")) ? 0 : Long.parseLong(item.get("bytes").toString());
            sessionsMerge += StringUtil.isEmpty(item.get("sessions")) ? 0 : Long.parseLong(item.get("sessions").toString());
            if (BooleanUtil.toBoolean(String.valueOf(item.get("isCDN")))) {
                countCDN++;
            }
            if (!StringUtil.startsWith(String.valueOf(item.get("ssl_sni")), "*")
                    && !StringUtil.startsWith(String.valueOf(item.get("ssl_sni")), "$")) {
                item.replace("ssl_sni", "$".concat(String.valueOf(item.get("ssl_sni"))));
            }
            item.remove("sessions");
            item.remove("bytes");
            item.remove("isCDN");
        }

        baseResult.setMessage(StrUtil.format("Top {} SNI, {} of ssl bytes, {} of ssl sessions, {} popular CDNs",
                result.size(),
                totalBytes == 0 ? "-" : NumberUtil.decimalFormat("#.##%", (bytesMerge * 1.0 / totalBytes)),
                totalSessions == 0 ? "-" : NumberUtil.decimalFormat("#.##%", (sessionsMerge * 1.0 / totalSessions)),
                countCDN == 0 ? "-" : countCDN));
        baseResult.setData(result);
        Map statistics = baseResult.getStatistics();
        statistics.put("elapsed", watch.elapsed(TimeUnit.MILLISECONDS) + (StringUtil.isEmpty(statistics.get("elapsed")) ? 0 : Long.parseLong(statistics.get("elapsed").toString())));
        statistics.put("result_rows", result.size());
        statistics.put("result_size", baseResult.getData().toString().getBytes().length);
        return baseResult;
    }

    @Override
    public List<Map<String, Object>> mergeSNI(List<Map<String, Object>> data) {
        Map<String, List<String>> cdn = buildDomainHashTable(getCfgCDN());
        List<Map<String, Object>> dataRec = Lists.newArrayList();
        Map<String, Map<String, Object>> dataSLD = Maps.newHashMap();
        Map<String, Map<String, Object>> dataMerge = Maps.newHashMapWithExpectedSize(data.size() / 4);
        Iterator<Map<String, Object>> iterator = data.iterator();
        while (iterator.hasNext()) {
            Map<String, Object> datum = iterator.next();
            String value = String.valueOf(datum.get("ssl_sni"));
            if (StringUtil.isBlank(value) || isTSLDomain(value)) {
                continue;
            }
            String key = getTopPrivateDomain(value);
            //1. filter exception data
            if (StringUtil.isBlank(key)) {
                dataRec.add(datum);
                continue;
            }
            String domain = FormatUtils.getDomain(value);
            //2. filter CDN
            if (inCDN(cdn, domain)) {
                datum.put("isCDN", true);
                dataRec.add(datum);
                continue;
            }

            //3 filter TopPrivateDomain
            if (isSLDDomain(domain)) {
                Map<String, Object> item = dataSLD.get(key);
                if (StringUtil.isNotEmpty(item)) {
                    long bytesBefore = StringUtil.isEmpty(item.get("bytes")) ? 0 : Long.parseLong(item.get("bytes").toString());
                    long sessionsBefore = StringUtil.isEmpty(item.get("sessions")) ? 0 : Long.parseLong(item.get("sessions").toString());
                    long bytesAfter = StringUtil.isEmpty(datum.get("bytes")) ? 0 : Long.parseLong(datum.get("bytes").toString());
                    long sessionsAfter = StringUtil.isEmpty(datum.get("sessions")) ? 0 : Long.parseLong(datum.get("sessions").toString());
                    datum.put("bytes", bytesBefore + bytesAfter);
                    datum.put("sessions", sessionsBefore + sessionsAfter);
                }
                datum.put("ssl_sni", key);
                dataSLD.put(key, datum);
                continue;
            }

            //4. first add
            if (!dataMerge.containsKey(key)) {
                dataMerge.put(key, datum);
                continue;
            }

            //5. merge
            Map<String, Object> item = dataMerge.get(key);
            long bytesBefore = StringUtil.isEmpty(item.get("bytes")) ? 0 : Long.parseLong(item.get("bytes").toString());
            long sessionsBefore = StringUtil.isEmpty(item.get("sessions")) ? 0 : Long.parseLong(item.get("sessions").toString());
            long bytesAfter = StringUtil.isEmpty(datum.get("bytes")) ? 0 : Long.parseLong(datum.get("bytes").toString());
            long sessionsAfter = StringUtil.isEmpty(datum.get("sessions")) ? 0 : Long.parseLong(datum.get("sessions").toString());
            item.put("ssl_sni", "*.".concat(key));
            item.put("bytes", bytesBefore + bytesAfter);
            item.put("sessions", sessionsBefore + sessionsAfter);
        }
        List<Map<String, Object>> result = Lists.newArrayList();
        dataMerge.keySet().forEach(o -> result.add(dataMerge.get(o)));
        dataSLD.keySet().forEach(o -> result.add(dataSLD.get(o)));
        result.addAll(dataRec);
        return result;
    }

    @Override
    public Map<String, List<String>> buildDomainHashTable(Map<String, List<String>> cdn) {
        Map<String, List<String>> map = Maps.newHashMap();
        for (String key : cdn.keySet()) {
            List<String> list = cdn.get(key);
            if (StringUtil.isEmpty(list)) {
                continue;
            }
            list.forEach(o -> {
                String topPrivateDomain = getTopPrivateDomain(o);
                if (StringUtil.isEmpty(map.get(topPrivateDomain))) {
                    map.put(topPrivateDomain, Lists.newArrayList(o));
                } else {
                    map.get(topPrivateDomain).add(o);
                }
            });
        }
        return map;
    }

    @Override
    public boolean inCDN(Map<String, List<String>> cdnMap, String domain) {
            String topPrivateDomain = getTopPrivateDomain(domain);
            List<String> list = cdnMap.get(topPrivateDomain);
            if (StringUtil.isEmpty(list)) {
                return false;
            }
            List<String> param = Splitter.on(".").omitEmptyStrings().splitToList(domain);
            out:
            for (String cdn : list) {
                List<String> flag = Splitter.on(".").omitEmptyStrings().splitToList(cdn);
                if (flag.size() > param.size()) {
                    continue;
                }
                for (int i = 1; i < flag.size() + 1; i++) {
                    if (!String.valueOf(param.get(param.size() - i)).equalsIgnoreCase(String.valueOf(flag.get(flag.size() - i)))) {
                        continue out;
                    }
                }
                return true;
            }
        return false;
    }

    private String getTopPrivateDomain(String domain) {
        String topPrivateDomain = null;
        try {
            topPrivateDomain = FormatUtils.getTopPrivateDomain(domain);
        } catch (RuntimeException outException) {
            log.warn("Parse top-level domain exceptions, exception domain names:" + domain);
        }
        return topPrivateDomain;
    }

    public boolean isTSLDomain(String domain) {
        try {
            domain = FormatUtils.getDomain(domain);
            if (!StringUtil.isBlank(domain) && InternetDomainName.isValid(domain)) {
                InternetDomainName internetDomainName = InternetDomainName.from(domain);
                return StringUtil.isEmpty(internetDomainName.publicSuffix()) ? false : internetDomainName.isPublicSuffix();
            } else {
                return false;
            }
        } catch (RuntimeException exception) {
            log.warn("valid isTSL domain exceptions, exception domain names:" + domain);
        }
        return false;
    }

    public boolean isSLDDomain(String domain) {
        try {
            if (!StringUtil.isBlank(domain) && InternetDomainName.isValid(domain)) {
                InternetDomainName internetDomainName = InternetDomainName.from(domain);
                return internetDomainName.isTopPrivateDomain();
            } else {
                return false;
            }
        } catch (RuntimeException exception) {
            log.warn("valid isSLD domain exceptions, exception domain names:" + domain);
        }
        return false;
    }


    private Map<String, List<String>> getCfgCDN() {
        Object codeInfo = metadataService.getCfg("public_code_info.json");
        if (StringUtil.isNotEmpty(codeInfo)) {
            Map<String, Map<String, List<String>>> data = (Map<String, Map<String, List<String>>>) JsonMapper.fromJsonString(codeInfo.toString(), Map.class);
            return Maps.newHashMap(data.get("CDN"));
        }
        return Maps.newHashMap();
    }

    private BaseResult getActiveClientIP(ComDSLObject.Query query) {

        ComDSLObject.Query.FilterBean filterBean = query.getParameters().getMatch().get(0);
        if (MatchEnum.EXACTLY.getType().equalsIgnoreCase(String.valueOf(filterBean.getType()))) {
            return query(generateActiveClientIP(query, env.getProperty("ENTITY_ACTIVE_CLIENT_IP"), query.getLimit()));
        } else {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), QGWMessageConst.MATCH_TYPE));
        }
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
        query.setLimit(StringUtil.isEmpty(query.getLimit()) ? String.valueOf(entityConfigSource.getActiveClientIP()) : query.getLimit());
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
        if (String.valueOf(ResultStatusEnum.SUCCESS.getCode()).equals(String.valueOf(baseResult.getStatus()))) {
            List<Object> alterationResultList = new ArrayList<>();
            List<Map> resultList = new ObjectMapper().convertValue(baseResult.getData(), new TypeReference<List<Map>>() {
            });
            resultList.forEach(r -> {
                if (ObjectUtil.isNotEmpty(r)) {
                    List<Object> list = (List<Object>) JsonMapper.fromJsonList(String.valueOf(r.get("client_ip")), Object.class);
                    alterationResultList.addAll(list);
                }
            });

            List<Object> sortedAndDistinctList = alterationResultList.stream()
                    .sorted(Comparator.comparingInt(x -> Integer.parseInt(String.valueOf(((Map) x).get("sessions")))).reversed())
                    .filter(distinctByKey(x -> String.valueOf(((Map) x).get("common_client_ip"))))
                    .collect(Collectors.toList());

            List<Object> dataResult = new ArrayList<>();
            for (Object o : sortedAndDistinctList) {
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

    /**
     * @param keyExtractor
     * @return java.util.function.Predicate<T>
     * @Description 自定义函数，根据value去重
     * @author wanghao
     * @date 2022/1/10 11:36
     */
    private static <T> Predicate<T> distinctByKey(Function<? super T, Object> keyExtractor) {
        Map<Object, Boolean> seen = new ConcurrentHashMap<>();
        return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
    }

    private BaseResult getResultOfServerIP(ComDSLObject.Query query) {
        String range = ComDSLParse.parseRange(query.getParameters().getRange());
        String timeInterval = getTimeInterval(query.getParameters(), -24 * 60);
        String datasource = query.getDataSource();
        String fieldWhere = timeInterval.concat(range);

        String tcpSessionSql = String.format(Objects.requireNonNull(env.getProperty("ENTITY_TCP_SESSION")), datasource, fieldWhere,
                StringUtil.isBlank(query.getLimit()) ? entityConfigSource.getTopServerIPByTCPSession() : query.getLimit());
        Map<String, String> tcpSessionResult = executeQuery(tcpSessionSql);

        String tcpClientIpsSql = String.format(Objects.requireNonNull(env.getProperty("ENTITY_TCP_UNIQ_CLIENT_IPS")), datasource, fieldWhere,
                StringUtil.isBlank(query.getLimit()) ? entityConfigSource.getTopServerIPByTCPUniqClientIP() : query.getLimit());
        Map<String, String> tcpClientIpResult = executeQuery(tcpClientIpsSql);

        String udpSessionSql = String.format(Objects.requireNonNull(env.getProperty("ENTITY_UDP_SESSION")), datasource, fieldWhere,
                StringUtil.isBlank(query.getLimit()) ? entityConfigSource.getTopServerIPByUDPBySession() : query.getLimit());
        Map<String, String> udpSessionResult = executeQuery(udpSessionSql);

        String udpClientIpsSql = String.format(Objects.requireNonNull(env.getProperty("ENTITY_UDP_UNIQ_CLIENT_IPS")), datasource, fieldWhere,
                StringUtil.isBlank(query.getLimit()) ? entityConfigSource.getTopServerIPByUDPUniqClientIP() : query.getLimit());
        Map<String, String> udpClientIpsResult = executeQuery(udpClientIpsSql);

        if (!String.valueOf(ResultStatusEnum.SUCCESS.getCode()).equals(tcpSessionResult.get("status"))
                || !String.valueOf(ResultStatusEnum.SUCCESS.getCode()).equals(tcpClientIpResult.get("status"))
                || !String.valueOf(ResultStatusEnum.SUCCESS.getCode()).equals(udpSessionResult.get("status"))
                || !String.valueOf(ResultStatusEnum.SUCCESS.getCode()).equals(udpClientIpsResult.get("status"))) {
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
        List<Map<String, Object>> dataList = (List<Map<String, Object>>) resultMap.get("data");
        List<Map> serverIpList = Lists.newArrayList();
        for (Map item : dataList) {
            Map<String, String> serverIpMap = Maps.newHashMap();
            serverIpMap.put("server_ip", String.valueOf(item.get("server_ip")));
            serverIpMap.put("vsys_id",String.valueOf(item.get("vsys_id")));
            serverIpList.add(serverIpMap);
        }
        return serverIpList;
    }

    public String generateActiveClientIP(ComDSLObject.Query query, String fileSql, String limit) {
        String range = ComDSLParse.parseRange(query.getParameters().getRange());
        String timeInterval = getTimeInterval(query.getParameters(), -30);
        String matchDate = ComDSLParse.parseMath(query.getParameters().getMatch());
        return String.format(fileSql, query.getDataSource(), timeInterval, range.concat(matchDate), limit);
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

    public BaseResult query(String sql) {
        BaseResult baseResult;
        Map<String, String> result = executeQuery(sql);
        if (String.valueOf(ResultStatusEnum.SUCCESS.getCode()).equals(result.get("status"))) {
            Map resultMap = (Map) JsonMapper.fromJsonString(result.get("result"), Map.class);
            List<Map> data = (List<Map>) resultMap.get("data");
            baseResult = BaseResultGenerator.success("ok", data, (Map) resultMap.get("statistics"));
        } else {
            baseResult = BaseResultGenerator.error(result.get("result"));
        }
        return baseResult;
    }

    public String generateTopSNI(ComDSLObject.Query query, String fileSql) {
        String range = ComDSLParse.parseRange(query.getParameters().getRange());
        String timeInterval = getTimeInterval(query.getParameters(), -24 * 60);
        String datasource = query.getDataSource();
        return String.format(fileSql, datasource, timeInterval.concat(range), entityConfigSource.getTopSNIDataset());
    }
    public String generateSNITotal(ComDSLObject.Query query, String fileSql) {
        String range = ComDSLParse.parseRange(query.getParameters().getRange());
        String timeInterval = getTimeInterval(query.getParameters(), -24 * 60);
        String datasource = query.getDataSource();
        return String.format(fileSql, datasource, timeInterval.concat(range));
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
