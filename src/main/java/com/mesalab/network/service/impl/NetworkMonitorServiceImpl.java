package com.mesalab.network.service.impl;

import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mesalab.cn.enums.RangeTypeEnum;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.common.utils.TreeUtils;
import com.mesalab.qgw.model.api.HttpConfig;
import com.mesalab.knowledge.enums.MatchEnum;
import com.mesalab.network.common.Constants;
import com.mesalab.network.dsl.DSLObject;
import com.mesalab.network.service.NetworkMonitorService;
import com.mesalab.network.model.protocol.ProtocolTree;
import com.mesalab.qgw.service.impl.HttpClientService;
import com.mesalab.services.common.dsl.ComDSLParse;
import com.mesalab.services.common.property.SqlPropertySourceFactory;
import com.zdjizhi.utils.StringUtil;
import org.joda.time.Period;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.*;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author wangwei
 * @version 1.0
 * @date 2020/6/30 4:24 下午
 */
@Service("networkMonitorService")
@PropertySource(value = "classpath:http-sql-template.sql", factory = SqlPropertySourceFactory.class)
public class NetworkMonitorServiceImpl implements NetworkMonitorService {
    private static final Log log = LogFactory.get();
    @Autowired
    HttpClientService httpClientService;
    @Autowired
    HttpConfig httpConfig;
    @Autowired
    Environment environment;
    private static final String localHostAddress;

    public String getPort() {
        return environment.getProperty("local.server.port");
    }
    private static final Map<String, String> LOGICAL_FIELD = Maps.newHashMap();

    static {
        localHostAddress = NetUtil.getLocalhostStr();
        LOGICAL_FIELD.put("data_center", "common_data_center");
        LOGICAL_FIELD.put("device_group", "common_device_group");
        LOGICAL_FIELD.put("vsys_id", "common_vsys_id");
        LOGICAL_FIELD.put("protocol_id", "common_protocol_label");
        LOGICAL_FIELD.put("app_name", "common_app_label");
    }

    @Override
    public BaseResult buildProtocolTree(DSLObject dslObject) {
        BaseResult baseResult;
        String sql = generateProtocolTreeSql(dslObject.getQuery(), environment.getProperty("PROTOCOL_TREE_SUMMARY"));
        Map<String, String> result = executeQuery(sql);
        if (String.valueOf(ResultStatusEnum.SUCCESS.getCode()).equals(result.get("status"))) {
            Map resultMap = (Map) JsonMapper.fromJsonString(result.get("result"), Map.class);
            List<Map> resultList = (List<Map>) resultMap.get("data");
            if (StringUtil.isEmpty(resultList)) {
                return BaseResultGenerator.success("ok", resultList, (Map) resultMap.get("statistics"));
            }
            List<ProtocolTree> protocolTreeList = buildHierarchicalStructure(buildFlatStructure(resultList));
            baseResult = BaseResultGenerator.success("ok", protocolTreeList, (Map) resultMap.get("statistics"));
        } else {
            baseResult = BaseResultGenerator.error(result.get("result"));
        }
        return baseResult;
    }

    @Override
    public BaseResult buildNetworkOverviewResult(DSLObject dslObject) {
        Map<String, String> dataRateResult = executeQuery(generateNetworkStatSummarySql(dslObject.getQuery(), environment.getProperty("NETWORK_STATISTICS_SUMMARY")));
        if (!String.valueOf(ResultStatusEnum.SUCCESS.getCode()).equals(dataRateResult.get("status"))) {
            throw new BusinessException(Integer.parseInt(dataRateResult.get("status")), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(), dataRateResult.get("message")));
        }
        Map<String, String> currentSessionResult = executeQuery(generateDynamicStatSql(dslObject.getQuery(), environment.getProperty("TRAFFIC_DYNAMIC_STATISTICS")));
        if (!String.valueOf(ResultStatusEnum.SUCCESS.getCode()).equals(currentSessionResult.get("status"))) {
            throw new BusinessException(Integer.parseInt(currentSessionResult.get("status")), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(), dataRateResult.get("message")));
        }
        Map<String, String> appStatisticsResult = executeQuery(generateAppStatisticsSql(dslObject.getQuery(), environment.getProperty("APP_STATISTICS_SUMMARY")));
        if (!String.valueOf(ResultStatusEnum.SUCCESS.getCode()).equals(appStatisticsResult.get("status"))) {
            throw new BusinessException(Integer.parseInt(appStatisticsResult.get("status")), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(), dataRateResult.get("message")));
        }

        Map statDataResult = JsonMapper.fromJsonString(currentSessionResult.get("result"), Map.class);
        List<Map> statData = (List<Map>) statDataResult.get("data");
        Map summaryDataResult = JsonMapper.fromJsonString(dataRateResult.get("result"), Map.class);
        List<Map> summaryData = (List<Map>) summaryDataResult.get("data");
        Map appDataResult = JsonMapper.fromJsonString(appStatisticsResult.get("result"), Map.class);
        List<Map> appData = (List<Map>) appDataResult.get("data");

        Map<String, Object> data = new HashMap<>(16);
        if (!statData.isEmpty()) {
            data.putAll(statData.get(0));
        }

        if (!summaryData.isEmpty()) {
            data.putAll(summaryData.get(0));
        }

        if (!appData.isEmpty()) {
            data.putAll(appData.get(0));
        }

        long uniqClientIp = Long.parseLong(String.valueOf(
                data.get(Constants.NETWORK_OVERVIEW_METRIC_UNIQ_CLIENT_IP) == null ? 0 : data.get(Constants.NETWORK_OVERVIEW_METRIC_UNIQ_CLIENT_IP)));
        long totalSessions = Long.parseLong(String.valueOf(
                data.get(Constants.NETWORK_OVERVIEW_METRIC_TOTAL_SESSIONS) == null ? 0 : data.get(Constants.NETWORK_OVERVIEW_METRIC_TOTAL_SESSIONS)));
        long totalBytes = Long.parseLong(String.valueOf(
                data.get(Constants.NETWORK_OVERVIEW_METRIC_TOTAL_BYTES) == null ? 0 : data.get(Constants.NETWORK_OVERVIEW_METRIC_TOTAL_BYTES)));
        long totalPackets = Long.parseLong(String.valueOf(
                data.get(Constants.NETWORK_OVERVIEW_METRIC_TOTAL_PACKETS) == null ? 0 : data.get(Constants.NETWORK_OVERVIEW_METRIC_TOTAL_PACKETS)));
        long unknownAppBytes = Long.parseLong(String.valueOf(
                data.get(Constants.NETWORK_OVERVIEW_METRIC_UNKNOWN_APP_BYTES) == null ? 0 : data.get(Constants.NETWORK_OVERVIEW_METRIC_UNKNOWN_APP_BYTES)));
        long oneSidedConnections = Long.parseLong(String.valueOf(
                data.get(Constants.NETWORK_OVERVIEW_METRIC_ONE_SIDED_CONNECTIONS) == null ? 0 : data.get(Constants.NETWORK_OVERVIEW_METRIC_ONE_SIDED_CONNECTIONS)));
        long sequenceGapLossBytes = Long.parseLong(String.valueOf(
                data.get(Constants.NETWORK_OVERVIEW_METRIC_SEQUENCE_GAP_LOSS_BYTES) == null ? 0 : data.get(Constants.NETWORK_OVERVIEW_METRIC_SEQUENCE_GAP_LOSS_BYTES)));
        long fragmentationPackets = Long.parseLong(String.valueOf(
                data.get(Constants.NETWORK_OVERVIEW_METRIC_FRAGMENTATION_PACKETS) == null ? 0 : data.get(Constants.NETWORK_OVERVIEW_METRIC_FRAGMENTATION_PACKETS)));

        long dynamicSessions = Long.parseLong(String.valueOf(
                data.get(Constants.NETWORK_OVERVIEW_METRIC_DYNAMIC_SESSIONS) == null ? 0 : data.get(Constants.NETWORK_OVERVIEW_METRIC_DYNAMIC_SESSIONS)));
        long dynamicBytes = Long.parseLong(String.valueOf(
                data.get(Constants.NETWORK_OVERVIEW_METRIC_DYNAMIC_BYTES) == null ? 0 : data.get(Constants.NETWORK_OVERVIEW_METRIC_DYNAMIC_BYTES)));
        long dataRate = Long.parseLong(String.valueOf(
                data.get(Constants.NETWORK_OVERVIEW_METRIC_DATA_RATE) == null ? 0 : data.get(Constants.NETWORK_OVERVIEW_METRIC_DATA_RATE)));

        String unknownAppPercent = dynamicBytes == 0 ? String.valueOf(0)
                : NumberUtil.div(BigDecimal.valueOf(unknownAppBytes), BigDecimal.valueOf(dynamicBytes), 4).toPlainString();
        String oneSidedPercent = totalSessions == 0 ? String.valueOf(0)
                : NumberUtil.div(BigDecimal.valueOf(oneSidedConnections), BigDecimal.valueOf(totalSessions), 4).toPlainString();
        String sequenceGapLossBytesPercent = sequenceGapLossBytes == 0 ? String.valueOf(0)
                : NumberUtil.div(BigDecimal.valueOf(sequenceGapLossBytes), BigDecimal.valueOf(totalBytes + sequenceGapLossBytes), 4).toPlainString();
        String fragmentationPacketsPercent = totalPackets == 0 ? String.valueOf(0)
                : NumberUtil.div(BigDecimal.valueOf(fragmentationPackets), BigDecimal.valueOf(totalPackets), 4).toPlainString();


        List result = new ArrayList<>();
        Map resultMap = new LinkedHashMap<>();
        resultMap.put(Constants.NETWORK_OVERVIEW_METRIC_UNIQ_CLIENT_IP, uniqClientIp);
        resultMap.put(Constants.NETWORK_OVERVIEW_METRIC_TOTAL_SESSIONS, dynamicSessions);
        resultMap.put(Constants.NETWORK_OVERVIEW_METRIC_DATA_RATE, dataRate);
        resultMap.put(Constants.NETWORK_OVERVIEW_METRIC_TOTAL_BYTES, dynamicBytes);
        resultMap.put(Constants.NETWORK_OVERVIEW_METRIC_UNKNOWN_APP_BYTES, unknownAppBytes);
        resultMap.put(Constants.NETWORK_OVERVIEW_METRIC_UNKNOWN_APP_PERCENT, unknownAppPercent);
        resultMap.put(Constants.NETWORK_OVERVIEW_METRIC_ONE_SIDED_CONNECTIONS, oneSidedConnections);
        resultMap.put(Constants.NETWORK_OVERVIEW_METRIC_ONE_SIDED_PERCENT, oneSidedPercent);
        resultMap.put(Constants.NETWORK_OVERVIEW_METRIC_SEQUENCE_GAP_LOSS_BYTES, sequenceGapLossBytes);
        resultMap.put(Constants.NETWORK_OVERVIEW_METRIC_SEQUENCE_GAP_LOSS_PERCENT, sequenceGapLossBytesPercent);
        resultMap.put(Constants.NETWORK_OVERVIEW_METRIC_FRAGMENTATION_PACKETS, fragmentationPackets);
        resultMap.put(Constants.NETWORK_OVERVIEW_METRIC_FRAGMENTATION_PERCENT, fragmentationPacketsPercent);
        result.add(resultMap);

        Map statistics = (Map) summaryDataResult.get("statistics");
        Map statStatistics = (Map) statDataResult.get("statistics");
        Map appStatistics = (Map) appDataResult.get("statistics");
        for (Object key : statistics.keySet()) {
            if ("result_rows".equals(key.toString())) {
                continue;
            }
            statistics.put(key, Long.parseLong(statistics.get(key).toString()) + Long.parseLong(statStatistics.get(key).toString()) + Long.parseLong(appStatistics.get(key).toString()));
        }

        return BaseResultGenerator.success("ok", result, statistics);
    }

    @Override
    public BaseResult buildProtocolDataRateSummary(DSLObject dslObject) {
        BaseResult baseResult;
        String sql = generateProtocolDataRateSql(dslObject.getQuery(), environment.getProperty("PROTOCOL_DATA_RATE_SUMMARY"));
        Map<String, String> result = executeQuery(sql);
        if (String.valueOf(ResultStatusEnum.SUCCESS.getCode()).equals(result.get("status"))) {
            Map resultMap = (Map) JsonMapper.fromJsonString(result.get("result"), Map.class);
            List<Map> data = (List<Map>) resultMap.get("data");
            data.forEach(o -> {
                String[] protocolIds = String.valueOf(o.get("type")).split("\\.");
                String protocolId = protocolIds[protocolIds.length - 1];
                o.put("type", protocolId);
            });
            baseResult = BaseResultGenerator.success("ok", data, (Map) resultMap.get("statistics"));
        } else {
            baseResult = BaseResultGenerator.error(result.get("result"));
        }
        return baseResult;
    }

    private String generateDynamicStatSql(DSLObject.QueryBean queryParam, String sqlTemplate) {
        String whereOfTime = getWhereOfTime(queryParam.getParameters());
        String whereOfExactly = getWhereFilter(queryParam.getParameters(), false, null);
        String[] intervals = getIntervals(queryParam.getParameters().getIntervals());
        return String.format(sqlTemplate,
                "UNIX_TIMESTAMP('".concat(intervals[1]).concat("')"),
                "UNIX_TIMESTAMP('".concat(intervals[0]).concat("')"),
                whereOfTime, whereOfExactly, Constants.PROTOCOL_ETHERNET_NODE);
    }

    private String generateAppStatisticsSql(DSLObject.QueryBean queryParam, String sqlTemplate) {
        String whereOfTime = getWhereOfTime(queryParam.getParameters());
        String whereOfExactly = getWhereFilter(queryParam.getParameters(), false, null);
        return String.format(sqlTemplate, whereOfTime, whereOfExactly);
    }

    private String generateProtocolDataRateSql(DSLObject.QueryBean queryParam, String sqlTemplate) {
        String whereOfTime = getWhereOfTime(queryParam.getParameters());
        String whereOfExactly = getWhereFilter(queryParam.getParameters(), false, Lists.newArrayList("protocol_id"));
        String protocolStr = null;
        List<DSLObject.QueryBean.Parameters.FilterBean> match = queryParam.getParameters().getMatch();
        for (DSLObject.QueryBean.Parameters.FilterBean item : match) {
            if (MatchEnum.PREFIX.getType().equals(item.getType())) {
                String[] split = item.getFieldValues().get(0).split(",");
                protocolStr = split[0].replaceFirst(Constants.PROTOCOL_TREE_ROOT_NAME + Constants.ENCAPSULATION_PATH_SEPARATOR, "");
            }
        }
        return String.format(sqlTemplate,
                queryParam.getParameters().getGranularity(), queryParam.getDataSource(), whereOfTime, whereOfExactly, protocolStr, queryParam.getParameters().getGranularity(),
                queryParam.getParameters().getGranularity(), queryParam.getDataSource(), whereOfTime, whereOfExactly, protocolStr, "%",
                protocolStr.length() - protocolStr.replaceAll("\\.", "").length(), queryParam.getParameters().getGranularity());

    }

    private String generateNetworkStatSummarySql(DSLObject.QueryBean queryParam, String sqlTemplate) {
        String whereOfTime = getWhereOfTime(queryParam.getParameters());
        String whereOfExactly = getWhereFilter(queryParam.getParameters(), false, null);

        return String.format(sqlTemplate,
                queryParam.getDataSource(), whereOfTime, whereOfExactly);
    }

    private String getWhereOfTime(DSLObject.QueryBean.Parameters parameters) {
        if (CollectionUtils.isEmpty(parameters.getIntervals())) {
            return StringUtil.EMPTY;
        }
        StringBuffer whereOfTime = new StringBuffer();
        String[] intervals = getIntervals(parameters.getIntervals());

        whereOfTime.append("__time >= DATE_FORMAT(FROM_UNIXTIME(UNIX_TIMESTAMP('").append(intervals[0]).append("')), '%Y-%m-%d %H:%i:%s')")
                .append("AND __time < DATE_FORMAT(FROM_UNIXTIME(UNIX_TIMESTAMP(  '").append(intervals[1]).append("')), '%Y-%m-%d %H:%i:%s')");
        return whereOfTime.toString();
    }

    private String[] getIntervals(List<String> intervals) {
        return intervals.get(0).split("/");
    }

    private String getWhereFilter(DSLObject.QueryBean.Parameters parameters, boolean isLogicalField, List<String> skipFields) {
        List<String> filter = Lists.newArrayList();
        if (!CollectionUtils.isEmpty(parameters.getMatch())) {
            filter.addAll(parseMatch(parameters.getMatch(), isLogicalField, skipFields));
        }
        if (!CollectionUtils.isEmpty(parameters.getRange())) {
            filter.addAll(parseRange(parameters.getRange(), isLogicalField, skipFields));
        }
        String join = String.join(" ) AND ( ", filter);
        return StringUtil.isEmpty(join) ? StringUtil.EMPTY : " AND " + " ( " + join + " ) ";
    }

    public List<String> parseRange(List<DSLObject.QueryBean.Parameters.FilterBean> ranges, boolean isLogicalField, List<String> skipFields) {
        if (ObjectUtil.isEmpty(ranges)) {
            return Lists.newArrayList();
        }
        List<String> list = Lists.newArrayList();
        for (DSLObject.QueryBean.Parameters.FilterBean range : ranges) {
            if (StringUtil.isNotEmpty(skipFields) && skipFields.contains(range.getFieldKey())) {
                continue;
            }
            List<String> values = Lists.newArrayList();
            String fieldKey = range.getFieldKey();
            fieldKey = isLogicalField && LOGICAL_FIELD.containsKey(fieldKey) ? LOGICAL_FIELD.get(fieldKey) : fieldKey;
            String type = range.getType();
            List<String> fieldValues = range.getFieldValues();
            for (String value : fieldValues) {
                if (!NumberUtil.isNumber(value)) {
                    value = "'".concat(ComDSLParse.encode(value)).concat("'");
                }
                values.add(ComDSLParse.parseRange(value, type));
            }
            String join = String.join((RangeTypeEnum.NE.getType().equalsIgnoreCase(type) ? " AND " : " OR ") + fieldKey, values);
            if (StringUtil.isNotBlank(join)) {
                list.add(fieldKey + join);
            }
        }
        return list;
    }

    public List<String> parseMatch(List<DSLObject.QueryBean.Parameters.FilterBean> matches, boolean isLogicalField, List<String> skipFields) {
        if (ObjectUtil.isEmpty(matches)) {
            return Lists.newArrayList();
        }
        List<String> list = Lists.newArrayList();
        for (DSLObject.QueryBean.Parameters.FilterBean match : matches) {
            if (StringUtil.isNotEmpty(skipFields) && skipFields.contains(match.getFieldKey())) {
                continue;
            }
            List<String> values = Lists.newArrayList();
            String fieldKey = match.getFieldKey();
            fieldKey = isLogicalField && LOGICAL_FIELD.containsKey(fieldKey) ? LOGICAL_FIELD.get(fieldKey) : fieldKey;
            String type = match.getType();
            List<String> fieldValues = match.getFieldValues();
            for (String value : fieldValues) {
                values.add(ComDSLParse.parseMatch(ComDSLParse.encode(value), type));
            }
            String join = String.join(" OR " + fieldKey, values);
            if (StringUtil.isNotBlank(join)) {
                list.add(fieldKey + join);
            }
        }
        return list;
    }

    private String generateProtocolTreeSql(DSLObject.QueryBean queryParam, String sqlTemplate) {
        String whereOfTime = getWhereOfTime(queryParam.getParameters());
        String whereOfExactly = getWhereFilter(queryParam.getParameters(), false, null);
        return String.format(sqlTemplate,
                queryParam.getDataSource(), whereOfTime, whereOfExactly);
    }

    @Override
    public List<ProtocolTree> buildFlatStructure(List<Map> protocolData) {
        List<ProtocolTree> nodes = Lists.newArrayList();
        for (Map protocolMap : protocolData) {
            ProtocolTree protocolTree = convertStringToObject(JsonMapper.toJsonString(protocolMap));
            if (StringUtil.isNotEmpty(protocolTree)) {
                nodes.add(protocolTree);
            }

        }
        ProtocolTree root = new ProtocolTree(Constants.PROTOCOL_TREE_ROOT_NAME, Constants.PROTOCOL_TREE_ROOT_NAME, null);
        Map<String, Long> protocolsAndApplications = nodes.stream().collect(Collectors.groupingBy(ProtocolTree::getName, Collectors.counting()));
        root.addMetric(Constants.PROTOCOL_TREE_METRIC_ENCAPSULATION_PATHS, nodes.size());
        root.addMetric(Constants.PROTOCOL_TREE_METRIC_PROTOCOLS_AND_APPLICATIONS, protocolsAndApplications.size());
        List<ProtocolTree> roots = nodes.stream().filter(o -> StringUtil.isBlank(o.getParentId())).collect(Collectors.toList());
        roots.forEach(item -> {
            root.setSentBytes(root.getSentBytes() + item.getSentBytes());
            root.setReceivedBytes(root.getReceivedBytes() + item.getReceivedBytes());
            root.setTotalSentBytes(root.getTotalSentBytes() + item.getSentBytes());
            root.setTotalReceivedBytes(root.getTotalReceivedBytes() + item.getReceivedBytes());
        });
        nodes.forEach(item -> {
            item.setId(Constants.PROTOCOL_TREE_ROOT_NAME + Constants.ENCAPSULATION_PATH_SEPARATOR + item.getId());
        });
        nodes.add(root);
        return nodes;
    }

    private Map<String, String> executeQuery(String sql) {
        try {
            sql = URLEncoder.encode(sql, "utf-8").replaceAll("\\+", "%20");
        } catch (UnsupportedEncodingException e) {
            log.error("sql Encode error: ", e);
        }
        String queryURL = URLUtil.normalize(localHostAddress + ":" + getPort() + "/?query=");
        int socketTimeOut = httpConfig.getServerResponseTimeOut();
        return httpClientService.httpGet(queryURL + sql, socketTimeOut);
    }


    private ProtocolTree convertStringToObject(String protocolString) {
        ProtocolTree protocolTree = null;
        try {
            Map<String, Object> results = (Map<String, Object>) JsonMapper.fromJsonString(protocolString, Map.class);
            String protocolId = String.valueOf(results.get("protocol_id"));
            if (StringUtil.isBlank(protocolId)) {
                log.warn("Protocol ID is NULL {}", protocolString);
                return null;
            }
            long sessions = Long.parseLong(Optional.ofNullable(
                    results.get(Constants.PROTOCOL_TREE_METRIC_SESSIONS)).orElse(0).toString());
            long sentBytes = Long.parseLong(Optional.ofNullable(
                    results.get(Constants.PROTOCOL_TREE_METRIC_C2S_BYTE_NUM)).orElse(0).toString());
            long receivedBytes = Long.parseLong(Optional.ofNullable(
                    results.get(Constants.PROTOCOL_TREE_METRIC_S2C_BYTE_NUM)).orElse(0).toString());
            long sentPackets = Long.parseLong(Optional.ofNullable(
                    results.get(Constants.PROTOCOL_TREE_METRIC_C2S_PKT_NUM)).orElse(0).toString());
            long receivedPackets = Long.parseLong(Optional.ofNullable(
                    results.get(Constants.PROTOCOL_TREE_METRIC_S2C_PKT_NUM)).orElse(0).toString());
            List<String> protocols = Splitter.on(Constants.ENCAPSULATION_PATH_SEPARATOR).omitEmptyStrings().splitToList(protocolId);
            String protocolName = StringUtil.isNotEmpty(protocols) ? protocols.get(protocols.size() - 1) : null;
            protocolTree = new ProtocolTree(protocolId, protocolName, null);
            protocolTree.setSentBytes(sentBytes);
            protocolTree.setReceivedBytes(receivedBytes);
            protocolTree.getMetrics().put(Constants.PROTOCOL_TREE_METRIC_SENT_PACKETS, sentPackets);
            protocolTree.getMetrics().put(Constants.PROTOCOL_TREE_METRIC_RECEIVED_PACKETS, receivedPackets);
            protocolTree.getMetrics().put(Constants.PROTOCOL_TREE_METRIC_SESSIONS, sessions);
        } catch (BusinessException e) {
            log.error("Convert Json String to  Protocol Tree Object Error ", e);
        }

        return protocolTree;
    }

    @Override
    public List<ProtocolTree> buildHierarchicalStructure(List<ProtocolTree> nodes) {

        List<ProtocolTree> targetNodes = groupByName(nodes);

        targetNodes.sort((o1, o2) -> {
            long numThis = o1.getReceivedBytes() + o1.getSentBytes();
            long numParam = o2.getReceivedBytes() + o2.getSentBytes();
            if (numThis > numParam) {
                return -1;
            } else if (numThis < numParam) {
                return 1;
            } else {
                return 0;
            }
        });
        Map<String, List<ProtocolTree>> protocolMap = Maps.newLinkedHashMap();
        List<ProtocolTree> roots = targetNodes.stream().filter(o -> StringUtil.isBlank(o.getParentId())).collect(Collectors.toList());
        protocolMap.put(Constants.PROTOCOLS_PARENT_ID, roots);
        targetNodes.stream().filter(o -> StringUtil.isNotBlank(o.getParentId())).forEach(x -> {
            if (StringUtil.isEmpty(protocolMap.get(x.getParentId()))) {
                protocolMap.put(x.getParentId(), Lists.newArrayList(x));
            } else {
                protocolMap.get(x.getParentId()).add(x);
            }
        });

        List<ProtocolTree> protocolTreeList = TreeUtils.mergeTree(protocolMap, Constants.PROTOCOLS_PARENT_ID, ProtocolTree::getId, ProtocolTree::setChildrens);
        for (ProtocolTree protocolTree : protocolTreeList) {
            List<ProtocolTree> ethernetList = protocolTree.getChildrens().stream().filter(
                    p -> Constants.PROTOCOL_ETHERNET_NODE.equals(p.getName())).collect(Collectors.toList());
            if (protocolTree.getChildrens().size() != ethernetList.size()) {
                protocolTree.setChildrens(ethernetList);
                protocolTree.setReceivedBytes(StringUtil.isEmpty(ethernetList.get(0)) ? 0 : ethernetList.get(0).getReceivedBytes());
                protocolTree.setSentBytes(StringUtil.isEmpty(ethernetList.get(0)) ? 0 : ethernetList.get(0).getSentBytes());
                log.warn("Protocol Tree Exceptional data occurs and non-{} protocols are filtered out: {}", Constants.PROTOCOL_ETHERNET_NODE, protocolTree.getChildrens());
            }
        }

        return protocolTreeList;

    }

    @Override
    public BaseResult buildAppData(DSLObject dslObject) {
        String whereOfExactly = getWhereFilter(dslObject.getQuery().getParameters(), false, null);
        String[] intervals = getIntervals(dslObject.getQuery().getParameters().getIntervals());
        String whereOfTime = "__time >= DATE_FORMAT(FROM_UNIXTIME(UNIX_TIMESTAMP('".concat(intervals[0]).concat("')), '%Y-%m-%d %H:%i:%s')")
                .concat("AND __time < DATE_FORMAT(FROM_UNIXTIME(UNIX_TIMESTAMP(  '").concat(intervals[1]).concat("')), '%Y-%m-%d %H:%i:%s')");
        String granularity = dslObject.getQuery().getParameters().getGranularity();
        granularity = StringUtil.isEmpty(granularity) ? "PT15S" : granularity;
        Period period = Period.parse(granularity);
        Integer limit = dslObject.getQuery().getLimit();
        Integer offset = dslObject.getQuery().getOffset();
        String sql = String.format(Objects.requireNonNull(environment.getProperty("APP_DATA_SUMMARY")),
                period.toStandardSeconds().getSeconds(), dslObject.getQuery().getDataSource(), whereOfTime, whereOfExactly, granularity, StringUtil.isEmpty(limit) ? "" : " LIMIT " + ((StringUtil.isEmpty(offset)) ? "" : offset + " ,") + limit);
        Map<String, String> dataResult = executeQuery(sql);
        if (String.valueOf(ResultStatusEnum.SUCCESS.getCode()).equals(dataResult.get("status"))) {
            return JsonMapper.fromJsonString(dataResult.get("result"), BaseResult.class);
        } else {
            throw new BusinessException(dataResult.get("result"));
        }
    }

    @Override
    public BaseResult buildInternalIPData(DSLObject dslObject) {
        String whereOfExactly = getWhereFilter(dslObject.getQuery().getParameters(), true, null);
        String[] intervals = getIntervals(dslObject.getQuery().getParameters().getIntervals());
        String whereOfTime = "common_recv_time >=  UNIX_TIMESTAMP('" + intervals[0] + "') AND common_recv_time <  UNIX_TIMESTAMP('" + intervals[1] + "')";
        Integer limit = dslObject.getQuery().getLimit();
        Integer offset = dslObject.getQuery().getOffset();
        String sql = String.format(Objects.requireNonNull(environment.getProperty("APP_INTERNAL_IP_SUMMARY")),
                dslObject.getQuery().getDataSource(), whereOfTime, whereOfExactly, StringUtil.isEmpty(limit) ? "" : " LIMIT " + ((StringUtil.isEmpty(offset)) ? "" : offset + " ,") + limit);
        Map<String, String> dataResult = executeQuery(sql);
        if (String.valueOf(ResultStatusEnum.SUCCESS.getCode()).equals(dataResult.get("status"))) {
            return JsonMapper.fromJsonString(dataResult.get("result"), BaseResult.class);
        } else {
            throw new BusinessException(dataResult.get("result"));
        }
    }

    @Override
    public BaseResult buildAppDataRate(DSLObject dslObject) {
        BaseResult baseResult;
        DSLObject.QueryBean.Parameters parameters = dslObject.getQuery().getParameters();
        String whereOfExactly = getWhereFilter(parameters, false, null);
        String whereOfTime = getWhereOfTime(parameters);
        String sql = String.format(environment.getProperty("APP_DATA_RATE_SUMMARY"),
                parameters.getGranularity(),
                dslObject.getQuery().getDataSource(),
                whereOfTime,
                whereOfExactly,
                parameters.getGranularity()
        );
        Map<String, String> result = executeQuery(sql);
        if (String.valueOf(ResultStatusEnum.SUCCESS.getCode()).equals(result.get("status"))) {
            Map resultMap = (Map) JsonMapper.fromJsonString(result.get("result"), Map.class);
            List<Map> data = (List<Map>) resultMap.get("data");
            baseResult = BaseResultGenerator.success("ok", data, (Map) resultMap.get("statistics"));
        } else {
            throw new BusinessException(result.get("result"));
        }
        return baseResult;
    }

    @Override
    public BaseResult buildAppTraffic(DSLObject dslObject) {
        BaseResult baseResult;
        DSLObject.QueryBean.Parameters parameters = dslObject.getQuery().getParameters();
        String whereOfExactly = getWhereFilter(parameters, false, null);
        String whereOfTime = getWhereOfTime(parameters);
        String sql = String.format(environment.getProperty("APP_TRAFFIC_SUMMARY"),
                dslObject.getQuery().getDataSource(),
                whereOfTime,
                whereOfExactly
        );
        Map<String, String> result = executeQuery(sql);
        if (String.valueOf(ResultStatusEnum.SUCCESS.getCode()).equals(result.get("status"))) {
            Map resultMap = (Map) JsonMapper.fromJsonString(result.get("result"), Map.class);
            List<Map> data = (List<Map>) resultMap.get("data");
            baseResult = BaseResultGenerator.success("ok", data, (Map) resultMap.get("statistics"));
        } else {
            throw new BusinessException(result.get("result"));
        }
        return baseResult;
    }

    /**
     * 基于Name聚合，统计Protocol和Application在全栈下的流量
     * @param nodes
     * @return
     */
    private List<ProtocolTree> groupByName(List<ProtocolTree> nodes) {
        Map<String, List<ProtocolTree>> resultMap = nodes.stream()
                .collect(Collectors.groupingBy(ProtocolTree::getName, Collectors.collectingAndThen(Collectors.toList(), protocolTreeList -> {

                    List<ProtocolTree> parentNodes = protocolTreeList.stream()
                            .filter(p -> StringUtil.isBlank(p.getParentId()) || !Splitter.on(".").omitEmptyStrings().splitToList(p.getParentId()).contains(p.getName()))
                            .collect(Collectors.toList());
                    Map<String, Long> summedMetrics = Maps.newLinkedHashMap();
                    Map<String, Long> totalBytesMetrics = Maps.newLinkedHashMap();
                    totalBytesMetrics.put(Constants.PROTOCOL_TREE_METRIC_TOTAL_SENT_BYTES, 0L);
                    totalBytesMetrics.put(Constants.PROTOCOL_TREE_METRIC_TOTAL_RECEIVED_BYTES, 0L);

                    parentNodes.stream().filter(p -> !p.getName().equalsIgnoreCase(Constants.PROTOCOL_TREE_ROOT_NAME)).forEach(protocolTree -> {
                        totalBytesMetrics.put(Constants.PROTOCOL_TREE_METRIC_TOTAL_SENT_BYTES, totalBytesMetrics.get(Constants.PROTOCOL_TREE_METRIC_TOTAL_SENT_BYTES) + protocolTree.getSentBytes());
                        totalBytesMetrics.put(Constants.PROTOCOL_TREE_METRIC_TOTAL_RECEIVED_BYTES, totalBytesMetrics.get(Constants.PROTOCOL_TREE_METRIC_TOTAL_RECEIVED_BYTES) + protocolTree.getReceivedBytes());
                        for (String key : protocolTree.getMetrics().keySet()) {
                            String totalKey = StrUtil.upperFirstAndAddPre(key, "total");
                            if (StringUtil.isEmpty(summedMetrics.get(totalKey))) {
                                summedMetrics.put(totalKey, Long.valueOf(protocolTree.getMetrics().get(key).toString()));
                            } else {
                                summedMetrics.put(totalKey, summedMetrics.get(totalKey)
                                        + Long.parseLong(protocolTree.getMetrics().get(key).toString()));
                            }
                        }
                    });

                    protocolTreeList.stream().filter(p -> !p.getName().equalsIgnoreCase(Constants.PROTOCOL_TREE_ROOT_NAME)).forEach(protocolTree -> {
                        protocolTree.setTotalSentBytes(totalBytesMetrics.get(Constants.PROTOCOL_TREE_METRIC_TOTAL_SENT_BYTES));
                        protocolTree.setTotalReceivedBytes(totalBytesMetrics.get(Constants.PROTOCOL_TREE_METRIC_TOTAL_RECEIVED_BYTES));
                        protocolTree.getMetrics().putAll(summedMetrics);
                    });

                    return protocolTreeList;
                })));

        List<ProtocolTree> protocolTreeList = Lists.newArrayList();
        resultMap.values().forEach(o -> {
            protocolTreeList.addAll(o);
        });

        return protocolTreeList;
    }
}
