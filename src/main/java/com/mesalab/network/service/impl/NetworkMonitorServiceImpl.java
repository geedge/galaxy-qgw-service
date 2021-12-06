package com.mesalab.network.service.impl;

import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.common.utils.TreeUtils;
import com.mesalab.knowledge.common.utils.HttpConfig;
import com.mesalab.knowledge.enums.MatchEnum;
import com.mesalab.knowledge.enums.RangeEnum;
import com.mesalab.network.dsl.DSLObject;
import com.mesalab.network.service.NetworkMonitorService;
import com.mesalab.qgw.model.protocol.ProtocolTree;
import com.mesalab.qgw.service.impl.HttpClientService;
import com.mesalab.services.common.property.SqlPropertySourceFactory;
import com.zdjizhi.utils.StringUtil;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Nullable;
import java.io.*;
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
public class NetworkMonitorServiceImpl implements NetworkMonitorService , EnvironmentAware {
    private static final Log log = LogFactory.get();
    @Autowired
    HttpClientService httpClientService;
    @Autowired
    HttpConfig httpConfig;
    @Value("${server.port}")
    private int serverPort;
    private Environment env;
    private static String localHostAddress;

    private static final String PROTOCOL_NODE = "Protocols.";
    private static final String PROTOCOL_ROOT_NODE = "ETHERNET";
    private static final String PROTOCOL_ROOT_PARENT_ID = "ROOT";


    static {
        localHostAddress = NetUtil.getLocalhostStr();
    }
    @Override
    public BaseResult build(DSLObject dslObject) {
        BaseResult baseResult;
        if (QueryType.PROTOCOL_TREE_SUMMARY.getValue().equalsIgnoreCase(dslObject.getQuery().getQueryType())) {
            baseResult = getProtocolTree(generateProtocolTreeSql(dslObject.getQuery(), env.getProperty("NETWORK_TREE_SUMMARY")));
        } else if (QueryType.PROTOCOL_DATA_RATE_SUMMARY.getValue().equalsIgnoreCase(dslObject.getQuery().getQueryType())) {
            baseResult = getProtocolDataRateSummary(generateProtocolDataRateSql(dslObject.getQuery(), env.getProperty("NETWORK_DATA_SUMMARY")));
        } else if (QueryType.NETWORK_OVERVIEW_SUMMARY.getValue().equalsIgnoreCase(dslObject.getQuery().getQueryType())) {
            Map<String, String> dataRateResult = executeQuery(generateOverviewSummarySql(dslObject.getQuery(), env.getProperty("NETWORK_OVERVIEW_SUMMARY")));
            String statSql = generateStatSql(dslObject.getQuery(), env.getProperty("NETWORK_OVERVIEW_STAT"));
            Map<String, String> statResult = executeQuery(statSql);
            baseResult = buildNetworkOverviewResult(dataRateResult, statResult);
        } else {
            baseResult = BaseResultGenerator.failure(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(), "No match queryType");
        }
        if (!baseResult.isSuccess()) {
            throw new BusinessException(baseResult.getStatus(), baseResult.getCode(), baseResult.getMessage(), null);
        }
        return baseResult;
    }

    private BaseResult buildNetworkOverviewResult(Map<String, String> dataRateResult, Map<String, String> currentSessionResult) {
        BaseResult baseResult;
        if (String.valueOf(HttpStatus.SC_OK).equals(currentSessionResult.get("status"))
                && String.valueOf(HttpStatus.SC_OK).equals(dataRateResult.get("status"))) {
            Map statDataResult = (Map) JsonMapper.fromJsonString(currentSessionResult.get("result"), Map.class);
            List<Map> statData = (List<Map>) statDataResult.get("data");
            Map summaryDataResult = (Map) JsonMapper.fromJsonString(dataRateResult.get("result"), Map.class);
            List<Map> summaryData = (List<Map>) summaryDataResult.get("data");

            Map<String, Object> data = new HashMap<>(16);
            if (!statData.isEmpty()) {
                data.putAll(statData.get(0));
            }
            if (statData.size() >= 2) {
                data.put("current_sessions", statData.get(1).get("total_bytes"));
            }
            if (!summaryData.isEmpty()) {
                data.putAll(summaryData.get(0));
            }
            long uniqClientIp = Long.parseLong(String.valueOf(data.get("uniq_client_ip") == null ? 0 : data.get("uniq_client_ip")));
            long totalSessions = Long.parseLong(String.valueOf(data.get("total_sessions") == null ? 0 : data.get("total_sessions")));
            long summaryTotalSessions = Long.parseLong(String.valueOf(data.get("summarySessions") == null ? 0 : data.get("summarySessions")));
            long summaryTotalBytes = Long.parseLong(String.valueOf(data.get("summaryTotalBytes") == null ? 0 : data.get("summaryTotalBytes")));
            long summaryTotalPackets = Long.parseLong(String.valueOf(data.get("summaryTotalPackets") == null ? 0 : data.get("summaryTotalPackets")));
            long currentSessions = Long.parseLong(String.valueOf(data.get("current_sessions") == null ? 0 : data.get("current_sessions")));
            long dataRate = Long.parseLong(String.valueOf(data.get("data_rate") == null ? 0 : data.get("data_rate")));
            long totalBytes = Long.parseLong(String.valueOf(data.get("total_bytes") == null ? 0 : data.get("total_bytes")));
            long totalUncategorizedBytes = Long.parseLong(String.valueOf(data.get("total_uncategorized_bytes") == null ? 0 : data.get("total_uncategorized_bytes")));
            double totalUncategorizedPercent = summaryTotalBytes == 0 ? 0 : totalUncategorizedBytes * 1.0 / summaryTotalBytes;
            long oneSidedConnections = Long.parseLong(String.valueOf(data.get("one_sided_connections") == null ? 0 : data.get("one_sided_connections")));
            double oneSidedPercent = summaryTotalSessions == 0 ? 0 : oneSidedConnections * 1.0 / summaryTotalSessions;
            long sequenceGapLossBytes = Long.parseLong(String.valueOf(data.get("sequence_gap_loss_bytes") == null ? 0 : data.get("sequence_gap_loss_bytes")));
            double sequenceGapLossBytesPercent = sequenceGapLossBytes == 0 ? 0 : sequenceGapLossBytes * 1.0 / (summaryTotalBytes + sequenceGapLossBytes);
            long fragmentationPackets = Long.parseLong(String.valueOf(data.get("fragmentation_packets") == null ? 0 : data.get("fragmentation_packets")));
            double fragmentationPacketsPercent = summaryTotalPackets == 0 ? 0 : fragmentationPackets * 1.0 / summaryTotalPackets;

            List result = new ArrayList<>();
            Map resultMap = new LinkedHashMap<>();
            resultMap.put("uniq_client_ip", uniqClientIp);
            resultMap.put("total_sessions", totalSessions);
            resultMap.put("current_sessions", currentSessions);
            resultMap.put("data_rate", dataRate);
            resultMap.put("total_bytes", totalBytes);
            resultMap.put("total_uncategorized_bytes", totalUncategorizedBytes);
            resultMap.put("total_uncategorized_percent", Double.parseDouble(String.format("%.4f", totalUncategorizedPercent)));
            resultMap.put("one_sided_connections", oneSidedConnections);
            resultMap.put("one_sided_percent", Double.parseDouble(String.format("%.4f", oneSidedPercent)));
            resultMap.put("sequence_gap_loss_bytes", sequenceGapLossBytes);
            resultMap.put("sequence_gap_loss_percent", Double.parseDouble(String.format("%.4f", sequenceGapLossBytesPercent)));
            resultMap.put("fragmentation_packets", fragmentationPackets);
            resultMap.put("fragmentation_percent", Double.parseDouble(String.format("%.4f", fragmentationPacketsPercent)));
            result.add(resultMap);

            Map statistics = (Map) summaryDataResult.get("statistics");
            Map statStatistics = (Map) statDataResult.get("statistics");
            for (Object key : statistics.keySet()) {
                if ("result_rows".equals(key.toString())) {
                    continue;
                }
                statistics.put(key, Long.parseLong(statistics.get(key).toString()) + Long.parseLong(statStatistics.get(key).toString()));
            }

            baseResult = BaseResultGenerator.success("ok", result, statistics);
        } else {
            baseResult = BaseResultGenerator.error("dataRate result: " + dataRateResult.toString() + ";\n currentSession result is: " + currentSessionResult);
        }
        return baseResult;
    }

    private BaseResult getProtocolDataRateSummary(String sql) {
        BaseResult baseResult;
        Map<String, String> result = executeQuery(sql);
        if (String.valueOf(HttpStatus.SC_OK).equals(result.get("status"))) {
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

    private BaseResult getProtocolTree(String sql) {
        BaseResult baseResult;
        Map<String, String> result = executeQuery(sql);
        if (String.valueOf(HttpStatus.SC_OK).equals(result.get("status"))) {
            Map resultMap = (Map) JsonMapper.fromJsonString(result.get("result"), Map.class);
            List<Map> resultList = (List<Map>) resultMap.get("data");
            if(StringUtil.isEmpty(resultList)) {
                return BaseResultGenerator.success("ok", resultList, (Map) resultMap.get("statistics"));
            }
            List<ProtocolTree> protocolTreeList = buildHierarchicalStructure(getFlatStructure(resultList));
            baseResult = BaseResultGenerator.success("ok", protocolTreeList, (Map) resultMap.get("statistics"));
        } else {
            baseResult = BaseResultGenerator.error(result.get("result"));
        }
        return baseResult;
    }

    private String generateStatSql(DSLObject.QueryBean queryParam, String sqlTemplate) {
        String whereOfTime = getWhereOfTime(queryParam.getParameters());
        String whereOfExactly = getWhereOfExactly(queryParam.getParameters());
        String[] intervals = getIntervals(queryParam.getParameters().getIntervals());
        return String.format(sqlTemplate,
                intervals[1], intervals[0], whereOfTime, whereOfExactly, PROTOCOL_ROOT_NODE,
                whereOfTime, whereOfExactly, PROTOCOL_ROOT_NODE);
    }

    private String generateProtocolDataRateSql(DSLObject.QueryBean queryParam, String sqlTemplate) {
        String whereOfTime = getWhereOfTime(queryParam.getParameters());
        String whereOfExactly = getWhereOfExactly(queryParam.getParameters());
        String protocolStr = null;
        List<DSLObject.QueryBean.Parameters.FilterBean> match = queryParam.getParameters().getMatch();
        for (DSLObject.QueryBean.Parameters.FilterBean item : match) {
            if (MatchEnum.PREFIX.getType().equals(item.getType())) {
                String[] split = item.getFieldValues().get(0).split(",");
                protocolStr = split[0].replaceFirst(PROTOCOL_NODE, "");
            }
        }
        return String.format(sqlTemplate,
                queryParam.getParameters().getGranularity(), queryParam.getDataSource(), whereOfTime, whereOfExactly, protocolStr, queryParam.getParameters().getGranularity(),
                queryParam.getParameters().getGranularity(), queryParam.getDataSource(), whereOfTime, whereOfExactly, protocolStr, "%",
                protocolStr.length() - protocolStr.replaceAll("\\.", "").length(), queryParam.getParameters().getGranularity());

    }

    private String generateOverviewSummarySql(DSLObject.QueryBean queryParam, String sqlTemplate) {
        String whereOfTime = getWhereOfTime(queryParam.getParameters());
        String whereOfExactly = getWhereOfExactly(queryParam.getParameters());

        return String.format(sqlTemplate,
                queryParam.getDataSource(), whereOfTime, whereOfExactly);
    }

    private String getWhereOfTime(DSLObject.QueryBean.Parameters parameters) {
        if (CollectionUtils.isEmpty(parameters.getIntervals())) {
            return StringUtil.EMPTY;
        }
        StringBuffer whereOfTime = new StringBuffer();
        String[] intervals = getIntervals(parameters.getIntervals());
        whereOfTime.append("__time >= TIMESTAMP '").append(intervals[0]).append("' AND __time < TIMESTAMP '").append(intervals[1]).append("'");
        return whereOfTime.toString();
    }

    private String[] getIntervals(List<String> intervals) {
        return intervals.get(0).split("/");
    }

    private String getWhereOfExactly(DSLObject.QueryBean.Parameters parameters) {
        ArrayList<DSLObject.QueryBean.Parameters.FilterBean> list = Lists.newArrayList();
        if (!CollectionUtils.isEmpty(parameters.getMatch())) {
            list.addAll(parameters.getMatch());
        }
        if (!CollectionUtils.isEmpty(parameters.getRange())) {
            list.addAll(parameters.getRange());
        }
        if (CollectionUtils.isEmpty(list)) {
            return StringUtil.EMPTY;
        }
        StringBuffer whereOfExactly = new StringBuffer();
        for (DSLObject.QueryBean.Parameters.FilterBean item : list) {
            if (MatchEnum.EXACTLY.getType().equalsIgnoreCase(item.getType()) && !CollectionUtils.isEmpty(item.getFieldValues())) {
                whereOfExactly.append(" AND ").append(item.getFieldKey()).append(" IN ('").append(String.join("','", item.getFieldValues())).append("')");
            } else if (RangeEnum.EQ.getType().equalsIgnoreCase(item.getType()) && !CollectionUtils.isEmpty(item.getFieldValues())) {
                whereOfExactly.append(" AND ").append(item.getFieldKey()).append(" IN ('").append(String.join("','", item.getFieldValues())).append("')");
            } else if (RangeEnum.NE.getType().equalsIgnoreCase(item.getType()) && !CollectionUtils.isEmpty(item.getFieldValues())) {
                whereOfExactly.append(" AND ").append(item.getFieldKey()).append(" NOT IN ('").append(String.join("','", item.getFieldValues())).append("')");
            }
        }
        return whereOfExactly.toString();
    }

    private String generateProtocolTreeSql(DSLObject.QueryBean queryParam, String sqlTemplate) {
        String whereOfTime = getWhereOfTime(queryParam.getParameters());
        String whereOfExactly = getWhereOfExactly(queryParam.getParameters());
        return String.format(sqlTemplate,
                queryParam.getDataSource(), whereOfTime, whereOfExactly);
    }

    private List<ProtocolTree> getFlatStructure(List<Map> protocolData) {
        List<ProtocolTree> listNodes = Lists.newArrayList();
        for (Map protocolMap : protocolData) {
            ProtocolTree protocolTree = convertStringToObject(JsonMapper.toJsonString(protocolMap));
            if(StringUtil.isNotEmpty(protocolTree)) {
                listNodes.add(protocolTree);
            }

        }
        ProtocolTree root = new ProtocolTree("Protocols", "Protocols", null);
        List<ProtocolTree> roots = listNodes.stream().filter(o -> StringUtil.isBlank(o.getParentId())).collect(Collectors.toList());
        roots.forEach(item -> {
            root.setSentBytes(root.getSentBytes() + item.getSentBytes());
            root.setReceivedBytes(root.getReceivedBytes() + item.getReceivedBytes());
        });

        listNodes.forEach(item -> {
            item.setId(PROTOCOL_NODE + item.getId());
        });

        listNodes.add(root);
        return listNodes;
    }

    private Map<String, String> executeQuery(String sql) {
        try {
            sql = URLEncoder.encode(sql, "utf-8").replaceAll("\\+", "%20");
        } catch (UnsupportedEncodingException e) {
            log.error("sql Encode error: ", e);
        }
        String queryURL = URLUtil.normalize(localHostAddress + ":" + serverPort + "/?query=");
        int socketTimeOut = httpConfig.getServerResponseTimeOut();
        return httpClientService.httpGet(queryURL + sql, socketTimeOut);
    }


    private ProtocolTree convertStringToObject(String protocolString) {
        ProtocolTree protocolTree = null;
        try {
           Map<String, Object> results = (Map<String, Object>) JsonMapper.fromJsonString(protocolString, Map.class);
           long sessions = Long.parseLong(results.get("sessions").toString());
           long sentBytes = Long.parseLong(results.get("c2s_byte_num").toString());
           long receivedBytes = Long.parseLong(results.get("s2c_byte_num").toString());
           long sentPackets = Long.parseLong(results.get("c2s_pkt_num").toString());
           long receivedPackets = Long.parseLong(results.get("s2c_pkt_num").toString());
           String protocolId = results.get("protocol_id").toString();
           List<String> protocols = Splitter.on(".").omitEmptyStrings().splitToList(protocolId);
           String protocolName = StringUtil.isNotEmpty(protocols) ? protocols.get(protocols.size()-1): null;
           protocolTree = new ProtocolTree(protocolId, protocolName, null);
           protocolTree.setSentBytes(sentBytes);
           protocolTree.setReceivedBytes(receivedBytes);
           protocolTree.getMetrics().put("sentPackets", sentPackets);
           protocolTree.getMetrics().put("receivedPackets", receivedPackets);
           protocolTree.getMetrics().put("sessions", sessions);
       } catch (BusinessException e) {
           log.error("Convert Json String to  Protocol Tree Object Error ", e);
       }

        return protocolTree;
    }

    private List<ProtocolTree> buildHierarchicalStructure(List<ProtocolTree> listNodes) {
        Map<String, List<ProtocolTree>> resultMap = listNodes.stream().collect(Collectors.groupingBy(ProtocolTree::getId));
        Map<String, Object> mergeResultMap =
                Maps.transformValues(resultMap, new com.google.common.base.Function<List<ProtocolTree>, Object>() {
                    @Nullable
                    @Override
                    public Object apply(@Nullable List<ProtocolTree> input) {
                        if (StringUtil.isEmpty(input)) {
                            return null;
                        }
                        if (input.size() == 1) {
                            return input.get(0);
                        }
                        ProtocolTree mergedProtocolTree = new ProtocolTree(input.get(0).getId(), input.get(0).getName(),
                                input.get(0).getParentId());
                        Map<String,Object> summedMetrics = Maps.newLinkedHashMap();
                        for (ProtocolTree protocolTree : input) {
                            mergedProtocolTree.setSentBytes(mergedProtocolTree.getSentBytes() + protocolTree.getSentBytes());
                            mergedProtocolTree.setReceivedBytes(mergedProtocolTree.getReceivedBytes() + protocolTree.getReceivedBytes());
                            for (String key : protocolTree.getMetrics().keySet()) {
                                if (StringUtil.isEmpty(summedMetrics.get(key))) {
                                    summedMetrics.put(key, protocolTree.getMetrics().get(key));
                                } else {
                                    long sumValue = Long.parseLong(summedMetrics.get(key).toString())
                                            + Long.parseLong(protocolTree.getMetrics().get(key).toString());
                                    summedMetrics.put(key, sumValue);
                                }


                            }
                        }
                        mergedProtocolTree.setMetrics(summedMetrics);

                        return mergedProtocolTree;
                    }
                });
        List<ProtocolTree> mergeResultList = new ArrayList(mergeResultMap.values());
        mergeResultList.sort(new Comparator<ProtocolTree>() {
            @Override
            public int compare(ProtocolTree o1, ProtocolTree o2) {
                return (o2.getReceivedBytes() + o2.getSentBytes()) - (o1.getReceivedBytes() + o1.getSentBytes()) > 0 ? 1 : -1;
            }
        });

        Map<String, List<ProtocolTree>> protocolMap = Maps.newLinkedHashMap();
        List<ProtocolTree> roots = mergeResultList.stream().filter(o -> StringUtil.isBlank(o.getParentId())).collect(Collectors.toList());
        protocolMap.put(PROTOCOL_ROOT_PARENT_ID, roots);
        mergeResultList.stream().filter(o -> StringUtil.isNotBlank(o.getParentId())).forEach(x->{
            if (StringUtil.isEmpty(protocolMap.get(x.getParentId()))) {
                protocolMap.put(x.getParentId(), Lists.newArrayList(x));
            } else {
                protocolMap.get(x.getParentId()).add(x);
            }
        });

        List<ProtocolTree> protocolTreeList = TreeUtils.mergeTree(protocolMap, PROTOCOL_ROOT_PARENT_ID, ProtocolTree::getId, ProtocolTree::setChildrens);
        for (ProtocolTree protocolTree : protocolTreeList) {
            List<ProtocolTree> ethernetList = protocolTree.getChildrens().stream().filter(
                    p -> PROTOCOL_ROOT_NODE.equals(p.getName())).collect(Collectors.toList());
            if (protocolTree.getChildrens().size() != ethernetList.size()) {
                protocolTree.setChildrens(ethernetList);
                protocolTree.setReceivedBytes(StringUtil.isEmpty(ethernetList.get(0)) ? 0 : ethernetList.get(0).getReceivedBytes());
                protocolTree.setSentBytes(StringUtil.isEmpty(ethernetList.get(0)) ? 0 : ethernetList.get(0).getSentBytes());
                log.warn("Protocol Tree Exceptional data occurs and non-{} protocols are filtered out: {}", PROTOCOL_ROOT_NODE, protocolTree.getChildrens());
            }
        }

        return protocolTreeList;

    }

    enum QueryType {

        PROTOCOL_TREE_SUMMARY("protocolTreeSummary"), PROTOCOL_DATA_RATE_SUMMARY("protocolDataRateSummary"), NETWORK_OVERVIEW_SUMMARY("networkOverviewSummary");
        private final String value;

        QueryType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
    @Override
    public void setEnvironment(Environment environment) {
        env = environment;
    }
}
