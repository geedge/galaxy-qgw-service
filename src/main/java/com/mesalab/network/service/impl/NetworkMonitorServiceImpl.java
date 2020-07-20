package com.mesalab.network.service.impl;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.ResultCodeEnum;
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
            e.printStackTrace();
            log.error("get LocalHost error: ", e);
        }
    }

    @Override
    public BaseResult getTrafficDistributedInfo(Param param) {
        String sql = null;
        if (QueryType.PROTOCOL_TREE_SUMMARY.getValue().equalsIgnoreCase(param.getQuery().getQueryType())) {
            sql = generateProtocolTreeSql(param.getQuery());
            return getResultProtocolTree(sql);
        } else if (QueryType.PROTOCOL_DATA_RATE_SUMMARY.getValue().equalsIgnoreCase(param.getQuery().getQueryType())) {
            String parameters = param.getQuery().getParameters();
            Optional.ofNullable(parameters).ifPresent(o -> {
                String[] split = parameters.split(",");
                param.getQuery().setParameters(split[0].replaceFirst(PROTOCOL_NODE, ""));
            });
            sql = generateProtocolDataRateSql(param.getQuery());
            return getProtocolDataRateSummary(sql);
        } else if (QueryType.NETWORK_OVERVIEW_SUMMARY.getValue().equalsIgnoreCase(param.getQuery().getQueryType())) {
            BaseResult baseResult;

            sql = generateDataRateSql(param.getQuery());
            Map<String, String> dataRateResult = executeQuery(sql);

            String currentSessionSql = generateCurrentSessionSql(param.getQuery());
            Map<String, String> currentSessionResult = executeQuery(currentSessionSql);

            baseResult = buildNetworkOverviewResult(dataRateResult, currentSessionResult);
            return baseResult;
        }
        return BaseResultGenerator.failure("No match queryType");
    }

    private BaseResult buildNetworkOverviewResult(Map<String, String> dataRateResult, Map<String, String> currentSessionResult) {
        BaseResult baseResult;
        if (String.valueOf(HttpStatus.SC_OK).equals(currentSessionResult.get("status"))
                && String.valueOf(HttpStatus.SC_OK).equals(dataRateResult.get("status"))) {
            Map session = (Map) JsonMapper.fromJsonString(currentSessionResult.get("result"), Map.class);
            List<Map> sessionData = (List<Map>) session.get("data");

            Map result = (Map) JsonMapper.fromJsonString(dataRateResult.get("result"), Map.class);
            List<Map> resultData = (List<Map>) result.get("data");

            for (int i = 0; i < resultData.size(); i++) {
                resultData.get(i).put("current_sessions", sessionData.get(i).get("current_sessions") == null ? 0 : sessionData.get(i).get("current_sessions"));
            }
            baseResult = BaseResultGenerator.success("ok", resultData, (Map) result.get("statistics"));
        } else {
            baseResult = BaseResultGenerator.error("dataRate result: " + dataRateResult.toString() + "; currentSession result is: " + currentSessionResult);
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
            baseResult = BaseResultGenerator.generate(Integer.parseInt(result.get("status")), ResultCodeEnum.SQL_EXECUTION_ERROR.getCode(), result.get("message"),
                    result.get("result"), null, null, null);
        }
        return baseResult;
    }

    private String generateCurrentSessionSql(QueryParam queryParam) {
        return String.format("SELECT SUM(sessions) AS current_sessions FROM %s " +
                        "WHERE __time >= TIMESTAMP '%s' and __time < TIMESTAMP '%s' " +
                        "GROUP BY __time ORDER BY __time DESC limit 1 ",
                queryParam.getDataSource(), queryParam.getIntervals().getStartTime(), queryParam.getIntervals().getEndTime());
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
                        "where protocol_id like CONCAT('%s','/%s') and __time >= TIMESTAMP '%s' and __time < TIMESTAMP '%s' and SUBSTRING(protocol_id, LENGTH(CONCAT('%s','/'))+1 )  NOT LIKE '%s/%s' " +
                        "group by TIME_FORMAT(MILLIS_TO_TIMESTAMP( 1000 * TIME_FLOOR_WITH_FILL(TIMESTAMP_TO_MILLIS(__time)/1000, '%s', 'zero')), 'yyyy-MM-dd HH:mm:ss'), protocol_id " +
                        "order by stat_time asc) ",
                queryParam.getGranularity(), queryParam.getDataSource(), queryParam.getParameters(), queryParam.getIntervals().getStartTime(), queryParam.getIntervals().getEndTime(), queryParam.getGranularity(),
                queryParam.getGranularity(), queryParam.getDataSource(), queryParam.getParameters(), '%', queryParam.getIntervals().getStartTime(), queryParam.getIntervals().getEndTime(), queryParam.getParameters(), '%', '%', queryParam.getGranularity());

    }

    private String generateDataRateSql(QueryParam queryParam) {
        return String.format("SELECT APPROX_COUNT_DISTINCT_DS_HLL(ip_object) AS uniq_client_ip, " +
                        "SUM(sessions) AS total_sessions, " +
                        "SUM(bytes) AS total_bytes, " +
                        "(SUM(bytes) * 8 * 1000)/(TIMESTAMP_TO_MILLIS(TIMESTAMP '%s')-TIMESTAMP_TO_MILLIS(TIMESTAMP '%s')) AS data_rate, " +
                        "SUM(one_sided_connections) AS one_sided_connections, " +
                        "SUM(uncategorized_bytes) AS total_uncategorized_bytes, " +
                        "CASE SUM(sessions) WHEN 0 THEN 0 ELSE ROUND(SUM(one_sided_connections)*1.0/SUM(sessions),4) END AS one_sided_percent, " +
                        "CASE SUM(bytes) WHEN 0 THEN 0 ELSE ROUND(SUM(uncategorized_bytes)*1.0/SUM(bytes),4) END AS total_uncategorized_percent " +
                        "FROM %s " +
                        "WHERE __time >= TIMESTAMP '%s' " +
                        "AND __time < TIMESTAMP '%s' LIMIT 1 ",
                queryParam.getIntervals().getEndTime(), queryParam.getIntervals().getStartTime(), queryParam.getDataSource(), queryParam.getIntervals().getStartTime(), queryParam.getIntervals().getEndTime());
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
            e.printStackTrace();
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
        protocolTree.getMetrics().put("sessions", sessions);
        protocolTree.getMetrics().put("receivedPackets", receivedPackets);
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
                        mergeProtocolTree.setMetrics(Maps.newHashMap());

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
