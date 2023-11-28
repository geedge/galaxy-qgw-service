package com.mesalab.qgw.service.impl;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.geedgenetworks.utils.StringUtil;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.TreeUtils;
import com.mesalab.network.common.Constants;
import com.mesalab.network.model.protocol.ProtocolTree;
import com.mesalab.qgw.model.basic.DSLProfile;
import com.mesalab.qgw.model.basic.QueryProfile;
import com.mesalab.qgw.service.DslService;
import com.mesalab.qgw.service.MetadataService;
import com.mesalab.qgw.service.QueryService;
import com.mesalab.services.common.property.SqlPropertySourceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * TODO
 *
 * @Classname DSLServiceImpl
 * @Date 2023/11/27 10:37
 * @Author wWei
 */
@Service
@PropertySource(value = "classpath:dsl-sql-template.sql", factory = SqlPropertySourceFactory.class)
public class DslServiceImpl implements DslService {

    private static final Log log = LogFactory.get();

    @Autowired
    Environment environment;
    @Autowired
    MetadataService metadataService;
    @Autowired
    QueryService queryService;

    private static final String TABLE_TRAFFIC_GENERAL_STAT = "traffic_general_stat";
    private static final String TABLE_APPLICATION_PROTOCOL_STAT = "application_protocol_stat";
    private static final String TABLE_SESSION_RECORD = "session_record";
    private static final String LOGICAL_TYPE_TIMESTAMP = "timestamp";
    private static final String LOGICAL_TYPE_UNIX_TIMESTAMP = "unix_timestamp";


    @Override
    public BaseResult appAndProtocolSummary(DSLProfile dslProfile) {
        BaseResult asymmetricFlowsResult = queryService.executeQuery(
                QueryProfile.builder().query(dslProfile.toSql(environment.getProperty("APPLICATION_AND_PROTOCOL_SUMMARY_ASYMMETRIC_FLOWS_STAT"), TABLE_TRAFFIC_GENERAL_STAT, metadataService.getPartitionKey(TABLE_TRAFFIC_GENERAL_STAT), LOGICAL_TYPE_TIMESTAMP)).build());
        if (!asymmetricFlowsResult.isSuccess()) {
            throw new BusinessException(asymmetricFlowsResult.getStatus(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(), asymmetricFlowsResult.getMessage()));
        }

        BaseResult networkTrafficStatResult = queryService.executeQuery(
                QueryProfile.builder().query(dslProfile.toSql(environment.getProperty("APPLICATION_AND_PROTOCOL_SUMMARY_TRAFFIC_STAT"), TABLE_APPLICATION_PROTOCOL_STAT, metadataService.getPartitionKey(TABLE_APPLICATION_PROTOCOL_STAT), LOGICAL_TYPE_TIMESTAMP)).build());
        if (!networkTrafficStatResult.isSuccess()) {
            throw new BusinessException(networkTrafficStatResult.getStatus(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(), networkTrafficStatResult.getMessage()));
        }

        BaseResult networkUniqueClientIpStatResult = queryService.executeQuery(
                QueryProfile.builder().query(dslProfile.toSql(environment.getProperty("APPLICATION_AND_PROTOCOL_SUMMARY_UNIQUE_CLIENT_IP_STAT"), TABLE_APPLICATION_PROTOCOL_STAT, metadataService.getPartitionKey(TABLE_APPLICATION_PROTOCOL_STAT), LOGICAL_TYPE_TIMESTAMP)).build());
        if (!networkUniqueClientIpStatResult.isSuccess()) {
            throw new BusinessException(networkUniqueClientIpStatResult.getStatus(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(), networkUniqueClientIpStatResult.getMessage()));
        }

        BaseResult networkTcpStatResult = queryService.executeQuery(
                QueryProfile.builder().query(dslProfile.toSql(environment.getProperty("APPLICATION_AND_PROTOCOL_SUMMARY_TCP_STAT"), TABLE_APPLICATION_PROTOCOL_STAT, metadataService.getPartitionKey(TABLE_APPLICATION_PROTOCOL_STAT), LOGICAL_TYPE_TIMESTAMP)).build());
        if (!networkTcpStatResult.isSuccess()) {
            throw new BusinessException(networkTcpStatResult.getStatus(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(), networkTcpStatResult.getMessage()));
        }

        BaseResult networkAppStatResult = queryService.executeQuery(
                QueryProfile.builder().query(dslProfile.toSql(environment.getProperty("APPLICATION_AND_PROTOCOL_SUMMARY_APP_STAT"), TABLE_APPLICATION_PROTOCOL_STAT, metadataService.getPartitionKey(TABLE_APPLICATION_PROTOCOL_STAT), LOGICAL_TYPE_TIMESTAMP)).build());
        if (!networkAppStatResult.isSuccess()) {
            throw new BusinessException(networkAppStatResult.getStatus(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(), networkAppStatResult.getMessage()));
        }

        List<Map> asymmetricFlowsData = (List<Map>) asymmetricFlowsResult.getData();
        List<Map> trafficStatData = (List<Map>) networkTrafficStatResult.getData();
        List<Map> uniqueClientIpStatData = (List<Map>) networkUniqueClientIpStatResult.getData();
        List<Map> tcpData = (List<Map>) networkTcpStatResult.getData();
        List<Map> appData = (List<Map>) networkAppStatResult.getData();

        Map<String, Object> data = Maps.newHashMap();

        if (!asymmetricFlowsData.isEmpty()) {
            data.putAll(asymmetricFlowsData.get(0));
        }
        if (!trafficStatData.isEmpty()) {
            data.putAll(trafficStatData.get(0));
        }
        if (!uniqueClientIpStatData.isEmpty()) {
            data.putAll(uniqueClientIpStatData.get(0));
        }
        if (!tcpData.isEmpty()) {
            data.putAll(tcpData.get(0));
        }
        if (!appData.isEmpty()) {
            data.putAll(appData.get(0));
        }

        long asymmetricFlows = Long.parseLong(String.valueOf(
                data.get(Constants.NETWORK_OVERVIEW_METRIC_ASYMMETRIC_FLOWS_BYTES) == null ? 0 : data.get(Constants.NETWORK_OVERVIEW_METRIC_ASYMMETRIC_FLOWS_BYTES)));
        long totalSessionUseOnAsymmetricFlows = Long.parseLong(String.valueOf(
                data.get(Constants.NETWORK_OVERVIEW_METRIC_TOTAL_BYTES_USE_ON_ASYMMETRIC_FLOWS) == null ? 0 : data.get(Constants.NETWORK_OVERVIEW_METRIC_TOTAL_BYTES_USE_ON_ASYMMETRIC_FLOWS)));

        long uniqueClientIp = Long.parseLong(String.valueOf(
                data.get(Constants.NETWORK_OVERVIEW_METRIC_UNIQ_CLIENT_IP) == null ? 0 : data.get(Constants.NETWORK_OVERVIEW_METRIC_UNIQ_CLIENT_IP)));
        long totalSessions = Long.parseLong(String.valueOf(
                data.get(Constants.NETWORK_OVERVIEW_METRIC_TOTAL_SESSIONS) == null ? 0 : data.get(Constants.NETWORK_OVERVIEW_METRIC_TOTAL_SESSIONS)));
        long totalBytes = Long.parseLong(String.valueOf(
                data.get(Constants.NETWORK_OVERVIEW_METRIC_TOTAL_BYTES) == null ? 0 : data.get(Constants.NETWORK_OVERVIEW_METRIC_TOTAL_BYTES)));
        long totalPackets = Long.parseLong(String.valueOf(
                data.get(Constants.NETWORK_OVERVIEW_METRIC_TOTAL_PACKETS) == null ? 0 : data.get(Constants.NETWORK_OVERVIEW_METRIC_TOTAL_PACKETS)));

        long fragmentationPackets = Long.parseLong(String.valueOf(
                data.get(Constants.NETWORK_OVERVIEW_METRIC_FRAGMENTATION_PACKETS) == null ? 0 : data.get(Constants.NETWORK_OVERVIEW_METRIC_FRAGMENTATION_PACKETS)));
        long dataRate = Long.parseLong(String.valueOf(
                data.get(Constants.NETWORK_OVERVIEW_METRIC_DATA_RATE) == null ? 0 : data.get(Constants.NETWORK_OVERVIEW_METRIC_DATA_RATE)));

        long tcpRetransmissionsPackets = Long.parseLong(String.valueOf(
                data.get(Constants.NETWORK_OVERVIEW_METRIC_TCP_RETRANSMISSIONS_PACKETS) == null ? 0 : data.get(Constants.NETWORK_OVERVIEW_METRIC_TCP_RETRANSMISSIONS_PACKETS)));
        long tcpTotalPackets = Long.parseLong(String.valueOf(
                data.get(Constants.NETWORK_OVERVIEW_METRIC_TCP_TOTAL_PACKETS) == null ? 0 : data.get(Constants.NETWORK_OVERVIEW_METRIC_TCP_TOTAL_PACKETS)));
        long unknownAppBytes = Long.parseLong(String.valueOf(
                data.get(Constants.NETWORK_OVERVIEW_METRIC_UNKNOWN_APP_BYTES) == null ? 0 : data.get(Constants.NETWORK_OVERVIEW_METRIC_UNKNOWN_APP_BYTES)));


        String unknownAppPercent = totalBytes == 0 ? String.valueOf(0)
                : NumberUtil.div(BigDecimal.valueOf(unknownAppBytes), BigDecimal.valueOf(totalBytes), 4).toPlainString();
        String asymmetricFlowsPercent = totalSessionUseOnAsymmetricFlows == 0 ? String.valueOf(0)
                : NumberUtil.div(BigDecimal.valueOf(asymmetricFlows), BigDecimal.valueOf(totalSessionUseOnAsymmetricFlows), 4).toPlainString();
        String tcpRetransmissionsPercent = tcpTotalPackets == 0 ? String.valueOf(0)
                : NumberUtil.div(BigDecimal.valueOf(tcpRetransmissionsPackets), BigDecimal.valueOf(tcpTotalPackets), 4).toPlainString();
        String fragmentationPacketsPercent = totalPackets == 0 ? String.valueOf(0)
                : NumberUtil.div(BigDecimal.valueOf(fragmentationPackets), BigDecimal.valueOf(totalPackets), 4).toPlainString();

        List result = new ArrayList<>();
        Map resultMap = new LinkedHashMap<>();
        resultMap.put(Constants.NETWORK_OVERVIEW_METRIC_UNIQ_CLIENT_IP, uniqueClientIp);
        resultMap.put(Constants.NETWORK_OVERVIEW_METRIC_TOTAL_SESSIONS, totalSessions);
        resultMap.put(Constants.NETWORK_OVERVIEW_METRIC_DATA_RATE, dataRate);
        resultMap.put(Constants.NETWORK_OVERVIEW_METRIC_TOTAL_BYTES, totalBytes);
        resultMap.put(Constants.NETWORK_OVERVIEW_METRIC_TOTAL_PACKETS, totalPackets);
        resultMap.put(Constants.NETWORK_OVERVIEW_METRIC_UNKNOWN_APP_BYTES, unknownAppBytes);
        resultMap.put(Constants.NETWORK_OVERVIEW_METRIC_UNKNOWN_APP_PERCENT, unknownAppPercent);
        resultMap.put(Constants.NETWORK_OVERVIEW_METRIC_ASYMMETRIC_FLOWS_BYTES, asymmetricFlows);
        resultMap.put(Constants.NETWORK_OVERVIEW_METRIC_ASYMMETRIC_FLOWS_PERCENT, asymmetricFlowsPercent);
        resultMap.put(Constants.NETWORK_OVERVIEW_METRIC_TCP_RETRANSMISSIONS_PACKETS, tcpRetransmissionsPackets);
        resultMap.put(Constants.NETWORK_OVERVIEW_METRIC_TCP_RETRANSMISSIONS_PERCENT, tcpRetransmissionsPercent);
        resultMap.put(Constants.NETWORK_OVERVIEW_METRIC_FRAGMENTATION_PACKETS, fragmentationPackets);
        resultMap.put(Constants.NETWORK_OVERVIEW_METRIC_FRAGMENTATION_PERCENT, fragmentationPacketsPercent);
        result.add(resultMap);

        Map statistics = asymmetricFlowsResult.getStatistics();
        Map networkTrafficStatistics = networkTrafficStatResult.getStatistics();
        Map networkUniqueClientIpStatistics = networkUniqueClientIpStatResult.getStatistics();
        Map networkTcpStatistics = networkTcpStatResult.getStatistics();
        Map networkAppStatistics = networkAppStatResult.getStatistics();
        for (Object key : statistics.keySet()) {
            if ("result_rows".equals(key.toString())) {
                continue;
            }
            statistics.put(key, Long.parseLong(networkTrafficStatistics.get(key).toString()) + Long.parseLong(networkUniqueClientIpStatistics.get(key).toString()) + Long.parseLong(networkTcpStatistics.get(key).toString()) + Long.parseLong(networkAppStatistics.get(key).toString()));
        }

        return BaseResultGenerator.success("ok", result, statistics);
    }

    @Override
    public BaseResult appAndProtocolTreeComposition(DSLProfile dslProfile) {
        String sql = dslProfile.toSql(environment.getProperty("APPLICATION_AND_PROTOCOL_TREE_COMPOSITION"), TABLE_APPLICATION_PROTOCOL_STAT, metadataService.getPartitionKey(TABLE_APPLICATION_PROTOCOL_STAT), LOGICAL_TYPE_TIMESTAMP);
        BaseResult baseResult = queryService.executeQuery(QueryProfile.builder().query(sql).build());
        if (!baseResult.isSuccess()) {
            throw new BusinessException(baseResult.getStatus(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(), baseResult.getMessage()));
        }
        List<Map> resultList = (List<Map>) baseResult.getData();
        if (StringUtil.isEmpty(resultList)) {
            return BaseResultGenerator.success("ok", resultList, baseResult.getStatistics());
        }
        List<ProtocolTree> protocolTreeList = buildHierarchicalStructure(buildFlatStructure(resultList));
        return BaseResultGenerator.success("ok", protocolTreeList, baseResult.getStatistics());
    }

    @Override
    public BaseResult applicationAndProtocolTreeThroughput(DSLProfile dslProfile) {
        String sql = dslProfile.toSql(environment.getProperty("APPLICATION_AND_PROTOCOL_TREE_THROUGHPUT"), TABLE_APPLICATION_PROTOCOL_STAT, metadataService.getPartitionKey(TABLE_APPLICATION_PROTOCOL_STAT), LOGICAL_TYPE_TIMESTAMP);
        BaseResult baseResult = queryService.executeQuery(QueryProfile.builder().query(sql).build());
        if (!baseResult.isSuccess()) {
            throw new BusinessException(baseResult.getStatus(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(), baseResult.getMessage()));
        }
        List<Map<String, Object>> data = (List<Map<String, Object>>) baseResult.getData();
        data.forEach(o -> {
            String[] protocolIds = String.valueOf(o.get("type")).split("\\.");
            String protocolId = protocolIds[protocolIds.length - 1];
            o.put("type", protocolId);
        });
        baseResult.setData(data);
        return baseResult;
    }

    @Override
    public BaseResult applicationAndProtocolTopApp(DSLProfile dslProfile) {
        if (StrUtil.isEmpty(dslProfile.getGranularity())) {
            dslProfile.setGranularity("PT15S");
        }
        String sql = dslProfile.toSql(environment.getProperty("APPLICATION_AND_PROTOCOL_TOP_APPS"), TABLE_APPLICATION_PROTOCOL_STAT, metadataService.getPartitionKey(TABLE_APPLICATION_PROTOCOL_STAT), LOGICAL_TYPE_TIMESTAMP);
        BaseResult baseResult = queryService.executeQuery(QueryProfile.builder().query(sql).build());
        if (!baseResult.isSuccess()) {
            throw new BusinessException(baseResult.getStatus(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(), baseResult.getMessage()));
        }
        return baseResult;
    }

    @Override
    public BaseResult applicationAndProtocolAppRelatedInternalIps(DSLProfile dslProfile) {
        String sql = dslProfile.toSql(environment.getProperty("APPLICATION_AND_PROTOCOL_APP_RELATED_INTERNAL_IPS"), TABLE_SESSION_RECORD, metadataService.getPartitionKey(TABLE_SESSION_RECORD), LOGICAL_TYPE_UNIX_TIMESTAMP);
        BaseResult baseResult = queryService.executeQuery(QueryProfile.builder().query(sql).build());
        if (!baseResult.isSuccess()) {
            throw new BusinessException(baseResult.getStatus(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(), baseResult.getMessage()));
        }
        return baseResult;
    }

    @Override
    public BaseResult applicationAndProtocolAppThroughput(DSLProfile dslProfile) {
        String sql = dslProfile.toSql(environment.getProperty("APPLICATION_AND_PROTOCOL_APP_THROUGHPUT"), TABLE_APPLICATION_PROTOCOL_STAT, metadataService.getPartitionKey(TABLE_APPLICATION_PROTOCOL_STAT), LOGICAL_TYPE_TIMESTAMP);
        BaseResult baseResult = queryService.executeQuery(QueryProfile.builder().query(sql).build());
        if (!baseResult.isSuccess()) {
            throw new BusinessException(baseResult.getStatus(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(), baseResult.getMessage()));
        }
        return baseResult;
    }

    @Override
    public BaseResult applicationAndProtocolAppSummary(DSLProfile dslProfile) {
        String sql = dslProfile.toSql(environment.getProperty("APPLICATION_AND_PROTOCOL_APP_SUMMARY"), TABLE_APPLICATION_PROTOCOL_STAT, metadataService.getPartitionKey(TABLE_APPLICATION_PROTOCOL_STAT), LOGICAL_TYPE_TIMESTAMP);
        BaseResult baseResult = queryService.executeQuery(QueryProfile.builder().query(sql).build());
        if (!baseResult.isSuccess()) {
            throw new BusinessException(baseResult.getStatus(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(), baseResult.getMessage()));
        }
        return baseResult;
    }

    @Override
    public List<ProtocolTree> buildFlatStructure(List<Map> protocolData) {
        List<ProtocolTree> nodes = Lists.newArrayList();
        for (Map protocolMap : protocolData) {
            ProtocolTree protocolTree = convertStringToObject(JSON.toJSONString(protocolMap, JSONWriter.Feature.LargeObject));
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

    /**
     * 基于Name聚合，统计Protocol和Application在全栈下的流量
     *
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

    private ProtocolTree convertStringToObject(String protocolString) {
        ProtocolTree protocolTree = null;
        try {
            Map<String, Object> results = JSON.parseObject(protocolString, Map.class);
            String protocolId = String.valueOf(results.get("protocol_stack_id"));
            if (StringUtil.isBlank(protocolId)) {
                log.warn("Protocol Stack ID is NULL {}", protocolString);
                return null;
            }
            long sessions = Long.parseLong(Optional.ofNullable(
                    results.get(Constants.PROTOCOL_TREE_METRIC_SESSIONS)).orElse(0).toString());
            long sentBytes = Long.parseLong(Optional.ofNullable(
                    results.get(Constants.PROTOCOL_TREE_METRIC_C2S_BYTES)).orElse(0).toString());
            long receivedBytes = Long.parseLong(Optional.ofNullable(
                    results.get(Constants.PROTOCOL_TREE_METRIC_S2C_BYTES)).orElse(0).toString());
            long sentPackets = Long.parseLong(Optional.ofNullable(
                    results.get(Constants.PROTOCOL_TREE_METRIC_C2S_PKTS)).orElse(0).toString());
            long receivedPackets = Long.parseLong(Optional.ofNullable(
                    results.get(Constants.PROTOCOL_TREE_METRIC_S2C_PKTS)).orElse(0).toString());
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

}
