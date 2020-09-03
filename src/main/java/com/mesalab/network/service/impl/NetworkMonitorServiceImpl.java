package com.mesalab.network.service.impl;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.common.utils.TreeUtils;
import com.mesalab.network.model.api.Param;
import com.mesalab.network.model.api.QueryParam;
import com.mesalab.qgw.model.protocol.ProtocolTree;
import com.mesalab.qgw.service.impl.HttpClientService;
import com.mesalab.network.service.NetworkMonitorService;
import com.zdjizhi.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author wangwei
 * @version 1.0
 * @date 2020/6/30 4:24 下午
 */
@Slf4j
@Service("networkMonitorService")
public class NetworkMonitorServiceImpl implements NetworkMonitorService {
    @Autowired
    HttpClientService httpClientService;
    @Value("${server.port}")
    private int serverPort;
    private static String hostAddress;

    private static final String PROTOCOL_NODE = "Protocols/";

    static {
        try {
            hostAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            log.error("get LocalHost error: ", e);
        }
    }

    @Override
    public BaseResult getTrafficDistributedInfo(Param param) {
        BaseResult baseResult;
        String sql = null;
        if (QueryType.PROTOCOL_TREE_SUMMARY.getValue().equalsIgnoreCase(param.getQuery().getQueryType())) {
            sql = generateProtocolTreeSql(param.getQuery());
            baseResult = getResultProtocolTree(sql);
        } else if (QueryType.PROTOCOL_DATA_RATE_SUMMARY.getValue().equalsIgnoreCase(param.getQuery().getQueryType())) {
            String parameters = param.getQuery().getParameters();
            Optional.ofNullable(parameters).ifPresent(o -> {
                String[] split = parameters.split(",");
                param.getQuery().setParameters(split[0].replaceFirst(PROTOCOL_NODE, ""));
            });
            sql = generateProtocolDataRateSql(param.getQuery());
            baseResult = getProtocolDataRateSummary(sql);
        } else if (QueryType.NETWORK_OVERVIEW_SUMMARY.getValue().equalsIgnoreCase(param.getQuery().getQueryType())) {
            sql = generateOverviewSummarySql(param.getQuery());
            Map<String, String> dataRateResult = executeQuery(sql);
            String statSql = generateStatSql(param.getQuery());
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
            long currentSessions = Long.parseLong(String.valueOf(data.get("current_sessions") == null ? 0 : data.get("current_sessions")));
            long dataRate = Long.parseLong(String.valueOf(data.get("data_rate") == null ? 0 : data.get("data_rate")));
            long totalBytes = Long.parseLong(String.valueOf(data.get("total_bytes") == null ? 0 : data.get("total_bytes")));
            long totalUncategorizedBytes = Long.parseLong(String.valueOf(data.get("total_uncategorized_bytes") == null ? 0 : data.get("total_uncategorized_bytes")));
            double totalUncategorizedPercent = totalBytes == 0 ? 0 : totalUncategorizedBytes * 1.0 / totalBytes;
            long oneSidedConnections = Long.parseLong(String.valueOf(data.get("one_sided_connections") == null ? 0 : data.get("one_sided_connections")));
            double oneSidedPercent = totalBytes == 0 ? 0 : oneSidedConnections * 1.0 / totalSessions;

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
                String[] protocolIds = String.valueOf(o.get("type")).split("/");
                String protocolId = protocolIds[protocolIds.length - 1];
                o.put("type", protocolId);
            });
            baseResult = BaseResultGenerator.success("ok", data, (Map) resultMap.get("statistics"));
        } else {
            baseResult = BaseResultGenerator.error(result.get("result"));
        }
        return baseResult;
    }

    private BaseResult getResultProtocolTree(String sql) {
        BaseResult baseResult;
        Map<String, String> result = executeQuery(sql);
        if (String.valueOf(HttpStatus.SC_OK).equals(result.get("status"))) {
            Map resultMap = (Map) JsonMapper.fromJsonString(result.get("result"), Map.class);
            List<ProtocolTree> listProtocol = getListProtocol(getProtocolTrees((List<Map>) resultMap.get("data")));
            baseResult = BaseResultGenerator.success("ok", listProtocol, (Map) resultMap.get("statistics"));
        } else {
            baseResult = BaseResultGenerator.error(result.get("result"));
        }
        return baseResult;
    }

    private String generateStatSql(QueryParam queryParam) {
        return String.format("(SELECT SUM(c2s_byte_num + s2c_byte_num) as total_bytes, SUM(sessions) as total_sessions, SUM(c2s_byte_num + s2c_byte_num) * 8000/(TIMESTAMP_TO_MILLIS(TIMESTAMP '%s')-TIMESTAMP_TO_MILLIS(TIMESTAMP '%s')) AS data_rate " +
                        "FROM traffic_protocol_stat_log " +
                        "WHERE __time >= TIMESTAMP '%s' AND __time < TIMESTAMP '%s' " +
                        "AND LENGTH(protocol_id) = LENGTH(REPLACE(protocol_id,'/','')) LIMIT 1) " +
                        "UNION ALL " +
                        "( SELECT SUM(sessions), 0, 0 " +
                        "FROM traffic_protocol_stat_log " +
                        "WHERE __time >= TIMESTAMP '%s' AND __time < TIMESTAMP '%s' " +
                        "AND LENGTH(protocol_id) = LENGTH(REPLACE(protocol_id,'/','')) GROUP BY __time ORDER BY __time DESC LIMIT 1 )",
                queryParam.getIntervals().getEndTime(), queryParam.getIntervals().getStartTime(), queryParam.getIntervals().getStartTime(), queryParam.getIntervals().getEndTime(),
                queryParam.getIntervals().getStartTime(), queryParam.getIntervals().getEndTime());
    }

    private String generateProtocolDataRateSql(QueryParam queryParam) {
        return String.format("(" +
                        "SELECT TIME_FORMAT(MILLIS_TO_TIMESTAMP( 1000 * TIME_FLOOR_WITH_FILL(TIMESTAMP_TO_MILLIS(__time)/1000, '%s', 'zero')), 'yyyy-MM-dd HH:mm:ss') as stat_time, protocol_id as type, sum(c2s_byte_num + s2c_byte_num) as bytes " +
                        "from %s " +
                        "where protocol_id = '%s' and __time >= TIMESTAMP '%s' and __time < TIMESTAMP '%s' " +
                        "group by TIME_FORMAT(MILLIS_TO_TIMESTAMP( 1000 * TIME_FLOOR_WITH_FILL(TIMESTAMP_TO_MILLIS(__time)/1000, '%s', 'zero')), 'yyyy-MM-dd HH:mm:ss'), protocol_id " +
                        "order by stat_time asc" +
                        ") " +
                        "union all " +
                        "(" +
                        "SELECT TIME_FORMAT(MILLIS_TO_TIMESTAMP( 1000 * TIME_FLOOR_WITH_FILL(TIMESTAMP_TO_MILLIS(__time)/1000, '%s', 'zero')), 'yyyy-MM-dd HH:mm:ss') as stat_time, protocol_id as type, sum(c2s_byte_num + s2c_byte_num) as bytes " +
                        "from %s " +
                        "where protocol_id like CONCAT('%s','/%s') and __time >= TIMESTAMP '%s' and __time < TIMESTAMP '%s' and LENGTH(protocol_id) = LENGTH(REPLACE(protocol_id,'/','')) + 1 + %s " +
                        "group by TIME_FORMAT(MILLIS_TO_TIMESTAMP( 1000 * TIME_FLOOR_WITH_FILL(TIMESTAMP_TO_MILLIS(__time)/1000, '%s', 'zero')), 'yyyy-MM-dd HH:mm:ss'), protocol_id " +
                        "order by stat_time asc) ",
                queryParam.getGranularity(), queryParam.getDataSource(), queryParam.getParameters(), queryParam.getIntervals().getStartTime(), queryParam.getIntervals().getEndTime(), queryParam.getGranularity(),
                queryParam.getGranularity(), queryParam.getDataSource(), queryParam.getParameters(), '%', queryParam.getIntervals().getStartTime(), queryParam.getIntervals().getEndTime(),
                queryParam.getParameters().length() - queryParam.getParameters().replaceAll("/", "").length(), queryParam.getGranularity());

    }

    private String generateOverviewSummarySql(QueryParam queryParam) {
        return String.format("SELECT APPROX_COUNT_DISTINCT_DS_HLL(ip_object) AS uniq_client_ip, " +
                        "SUM(one_sided_connections) AS one_sided_connections, " +
                        "SUM(uncategorized_bytes) AS total_uncategorized_bytes " +
                        "FROM %s " +
                        "WHERE __time >= TIMESTAMP '%s' " +
                        "AND __time < TIMESTAMP '%s' LIMIT 1 ",
                queryParam.getDataSource(), queryParam.getIntervals().getStartTime(), queryParam.getIntervals().getEndTime());
    }

    private String generateProtocolTreeSql(QueryParam queryParam) {
        return String.format("SELECT protocol_id, SUM(sessions) as sessions,SUM(c2s_byte_num) as c2s_byte_num, SUM(c2s_pkt_num) as c2s_pkt_num, SUM(s2c_byte_num) as s2c_byte_num, SUM(s2c_pkt_num) as s2c_pkt_num " +
                        "FROM %s " +
                        "WHERE __time >= TIMESTAMP '%s' AND __time < TIMESTAMP '%s' " +
                        "GROUP BY protocol_id ",
                queryParam.getDataSource(), queryParam.getIntervals().getStartTime(), queryParam.getIntervals().getEndTime());
    }

    private List<ProtocolTree> getProtocolTrees(List<Map> data) {
        Iterable<ProtocolTree> resultConcat = Iterables.concat(new ArrayList<>());
        for (Map datum : data) {
            String s = JsonMapper.toJsonString(datum);
            List<ProtocolTree> nodes = parserRawData(s);
            resultConcat = Iterables.concat(resultConcat, nodes);
        }
        List<ProtocolTree> listNodes = Lists.newArrayList(resultConcat);
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
            hostAddress = "127.0.0.1";
            log.error("sql Encode error: ", e);
        }
        String url = "http://" + hostAddress + ":" + serverPort + "/?query=";
        return httpClientService.httpGet(url + sql);
    }


    private List<ProtocolTree> parserRawData(String jsonString) {
        List<ProtocolTree> lists = Lists.newArrayList();
        Map<String, Object> results = (Map<String, Object>) JsonMapper.fromJsonString(jsonString, Map.class);
        long sessions = Long.parseLong(results.get("sessions").toString());
        long sentBytes = Long.parseLong(results.get("c2s_byte_num").toString());
        long receivedBytes = Long.parseLong(results.get("s2c_byte_num").toString());
        long sentPackets = Long.parseLong(results.get("c2s_pkt_num").toString());
        long receivedPackets = Long.parseLong(results.get("s2c_pkt_num").toString());
        String protocolId = results.get("protocol_id").toString();
        List<String> protocolList = Lists.newArrayList(protocolId.split("/"));
        ProtocolTree protocolTree = new ProtocolTree(protocolId, protocolList.size() <= 0 ? null : protocolList.get(protocolList.size() - 1), null);
        protocolTree.setSentBytes(sentBytes);
        protocolTree.setReceivedBytes(receivedBytes);
        protocolTree.getMetrics().put("sentPackets", sentPackets);
        protocolTree.getMetrics().put("receivedPackets", receivedPackets);
        protocolTree.getMetrics().put("sessions", sessions);
        lists.add(protocolTree);
        return lists;
    }

    private List<ProtocolTree> getListProtocol(List<ProtocolTree> listNodes) {
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
                        ProtocolTree firstProtocolTree = input.get(0);
                        ProtocolTree mergeProtocolTree = new ProtocolTree(firstProtocolTree.getId(), firstProtocolTree.getName(),
                                firstProtocolTree.getParentId(), 0, 0);
                        mergeProtocolTree.setMetrics(Maps.newLinkedHashMap());

                        for (ProtocolTree protocolTree : input) {
                            mergeProtocolTree.setSentBytes(mergeProtocolTree.getSentBytes() + protocolTree.getSentBytes());
                            mergeProtocolTree.setReceivedBytes(mergeProtocolTree.getReceivedBytes() + protocolTree.getReceivedBytes());
                            for (String key : protocolTree.getMetrics().keySet()) {
                                if (StringUtil.isEmpty(mergeProtocolTree.getMetrics().get(key))) {
                                    mergeProtocolTree.getMetrics().put(key, protocolTree.getMetrics().get(key));
                                } else {
                                    long sumValue = Long.parseLong(mergeProtocolTree.getMetrics().get(key).toString())
                                            + Long.parseLong(protocolTree.getMetrics().get(key).toString());
                                    mergeProtocolTree.getMetrics().put(key, sumValue);
                                }
                            }

                        }

                        return mergeProtocolTree;
                    }
                });
        List<ProtocolTree> mergeNodes = new ArrayList(mergeResultMap.values());
        return TreeUtils.mergeTree(mergeNodes, ProtocolTree::getId, ProtocolTree::getParentId, ProtocolTree::setChildrens);
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
}
