package com.mesalab.qgw.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.geedgenetworks.utils.IPUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.HttpStatusCodeEnum;
import com.mesalab.common.enums.QueryOption;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.qgw.constant.dsl.TrafficSpectrumConstants;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.qgw.model.basic.DSLQueryContext;
import com.mesalab.qgw.model.basic.DSLQueryRequestParam;
import com.mesalab.qgw.model.basic.EngineConfigSource;
import com.mesalab.qgw.model.basic.SQLQueryContext;
import com.mesalab.qgw.service.DatabaseService;
import com.mesalab.qgw.service.SQLSyncQueryService;
import com.mesalab.qgw.service.TrafficSpectrumDslService;
import com.mesalab.services.common.property.SqlPropertySourceFactory;
import com.mesalab.services.configuration.JobConfig;
import org.checkerframework.checker.nullness.qual.Nullable;
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
 * @Classname TrafficSpectrumDslServiceImpl
 * @Date 2024/5/10 15:01
 * @Author wWei
 */
@Service
@PropertySource(value = "classpath:dsl-sql-template.sql", factory = SqlPropertySourceFactory.class)
public class TrafficSpectrumDslServiceImpl implements TrafficSpectrumDslService {

    private static final Log log = LogFactory.get();
    private Environment environment;
    private DatabaseService databaseService;
    private SQLSyncQueryService sqlSyncQueryService;
    private EngineConfigSource engineConfigSource;
    private static final String TRAFFIC_SPECTRUM_SUMMARY = "TRAFFIC_SPECTRUM_SUMMARY";
    private static final String TRAFFIC_SPECTRUM_UNIQ_IP = "TRAFFIC_SPECTRUM_UNIQ_IP";
    private static final String TRAFFIC_SPECTRUM_APP_DISTRIBUTION_SERVER_IP = "TRAFFIC_SPECTRUM_APP_DISTRIBUTION_SERVER_IP";
    private static final String TRAFFIC_SPECTRUM_APP_DISTRIBUTION_SERVER_DOMAIN = "TRAFFIC_SPECTRUM_APP_DISTRIBUTION_SERVER_DOMAIN";
    private static final String TRAFFIC_SPECTRUM_CLIENT_IP_CONNECT_APPLICATION_USAGE = "TRAFFIC_SPECTRUM_CLIENT_IP_CONNECT_APPLICATION_USAGE";
    private static final String TRAFFIC_SPECTRUM_NETWORK_THROUGHPUT_TREND = "TRAFFIC_SPECTRUM_NETWORK_THROUGHPUT_TREND";
    private static final String TABLE_TRAFFIC_SKETCH_METRIC = "traffic_sketch_metric";
    private static final String TIME_FIELD_TYPE = "unix_timestamp";
    private static final String GRANULARITY_DEFAULT = "PT60S";

    @Override
    public BaseResult run(DSLQueryRequestParam dslQueryRequestParam) {
        if (JobConfig.TRAFFIC_SPECTRUM_SUMMARY.equals(dslQueryRequestParam.getName())) {
            return BaseResultGenerator.success(summary(dslQueryRequestParam));
        } else if (JobConfig.TRAFFIC_SPECTRUM_UNIQUE_CLIENT_AND_SERVER_IPS.equals(dslQueryRequestParam.getName())) {
            return BaseResultGenerator.success(uniqIpsStat(dslQueryRequestParam));
        } else if (JobConfig.TRAFFIC_SPECTRUM_APP_DISTRIBUTION.equals(dslQueryRequestParam.getName())) {
            return BaseResultGenerator.success(topServerIpAndServerDomain(dslQueryRequestParam));
        } else if (JobConfig.TRAFFIC_SPECTRUM_CLIENT_IP_CONNECT_APPLICATION_USAGE.equals(dslQueryRequestParam.getName())) {
            return BaseResultGenerator.success(internalExternalDimensionMetricsStat(dslQueryRequestParam));
        } else if (JobConfig.TRAFFIC_SPECTRUM_NETWORK_THROUGHPUT_TREND.equals(dslQueryRequestParam.getName())) {
            return BaseResultGenerator.success(networkThroughputTrend(dslQueryRequestParam));
        }
        throw new QGWBusinessException(HttpStatusCodeEnum.NOT_FOUND.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), dslQueryRequestParam.getName() + " was not found."));
    }

    @Override
    public List<Map<String, Object>> summary(DSLQueryRequestParam dslQueryRequestParam) {
        DSLQueryContext dslQueryContext = BeanUtil.copyProperties(dslQueryRequestParam, DSLQueryContext.class);
        String sql = dslQueryContext.toSql(
                environment.getProperty(TRAFFIC_SPECTRUM_SUMMARY)
                , TABLE_TRAFFIC_SKETCH_METRIC
                , databaseService.getPartitionKey(TABLE_TRAFFIC_SKETCH_METRIC)
                , TIME_FIELD_TYPE);
        BaseResult<List<Map<String, Object>>> baseResult = sqlSyncQueryService.executeQuery(
                SQLQueryContext.builder()
                        .originalSQL(sql)
                        .option(QueryOption.REAL_TIME.getValue())
                        .build());
        if (baseResult.isSuccess()) {
            baseResult.getData().forEach(map -> {
                long bytes = Long.parseLong(String.valueOf(map.get("total_bytes") == null ? 0 : map.get("total_bytes")));
                long packets = Long.parseLong(String.valueOf(map.get("total_packets") == null ? 0 : map.get("total_packets")));
                long sessions = Long.parseLong(String.valueOf(map.get("total_sessions") == null ? 0 : map.get("total_sessions")));
                long unknownAppBytes = Long.parseLong(String.valueOf(map.get("unknown_app_bytes") == null ? 0 : map.get("unknown_app_bytes")));
                long asymmetricFlows = Long.parseLong(String.valueOf(map.get("asymmetric_flows") == null ? 0 : map.get("asymmetric_flows")));
                long fragmentationPackets = Long.parseLong(String.valueOf(map.get("fragmentation_packets") == null ? 0 : map.get("fragmentation_packets")));
                HashMap<String, Object> item = Maps.newLinkedHashMap();
                item.put(TrafficSpectrumConstants.DIRECTION, map.get("direction"));
                item.put(TrafficSpectrumConstants.BYTES, bytes);
                item.put(TrafficSpectrumConstants.PACKETS, packets);
                item.put(TrafficSpectrumConstants.SESSIONS, sessions);
                item.put(TrafficSpectrumConstants.UNKNOWN_APP_BYTES, unknownAppBytes);
                item.put(TrafficSpectrumConstants.UNCATEGORIZED_PERCENT, bytes == 0 ? 0.0 : NumberUtil.div(BigDecimal.valueOf(unknownAppBytes), BigDecimal.valueOf(bytes), 4).doubleValue());
                item.put(TrafficSpectrumConstants.ASYMMETRIC_FLOWS, asymmetricFlows);
                item.put(TrafficSpectrumConstants.ASYMMETRIC_FLOWS_PERCENT, sessions == 0 ? 0.0 : NumberUtil.div(BigDecimal.valueOf(asymmetricFlows), BigDecimal.valueOf(sessions), 4).doubleValue());
                item.put(TrafficSpectrumConstants.FRAGMENTATION_PACKETS, fragmentationPackets);
                item.put(TrafficSpectrumConstants.FRAGMENTATION_PERCENT, packets == 0 ? 0.0 : NumberUtil.div(BigDecimal.valueOf(fragmentationPackets), BigDecimal.valueOf(packets), 4).doubleValue());
                map.clear();
                map.putAll(item);
            });
            return baseResult.getData();
        }
        throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(), baseResult.getMessage()));
    }

    @Override
    public List<Map<String, Object>> uniqIpsStat(DSLQueryRequestParam dslQueryRequestParam) {
        DSLQueryContext dslQueryContext = BeanUtil.copyProperties(dslQueryRequestParam, DSLQueryContext.class);
        String sql = dslQueryContext.toSql(
                environment.getProperty(TRAFFIC_SPECTRUM_UNIQ_IP)
                , TABLE_TRAFFIC_SKETCH_METRIC
                , databaseService.getPartitionKey(TABLE_TRAFFIC_SKETCH_METRIC)
                , TIME_FIELD_TYPE);
        BaseResult<List<Map<String, Object>>> baseResult = sqlSyncQueryService.executeQuery(
                SQLQueryContext.builder()
                        .originalSQL(sql)
                        .option(QueryOption.REAL_TIME.getValue())
                        .build());
        if (baseResult.isSuccess()) {
            baseResult.getData().forEach(map -> {
                long internalUniqClientIp = Long.parseLong(String.valueOf(map.get("internal_uniq_client_ip") == null ? 0 : map.get("internal_uniq_client_ip")));
                long internalUniqServerIp = Long.parseLong(String.valueOf(map.get("internal_uniq_server_ip") == null ? 0 : map.get("internal_uniq_server_ip")));
                long externalUniqClientIp = Long.parseLong(String.valueOf(map.get("external_uniq_client_ip") == null ? 0 : map.get("external_uniq_client_ip")));
                long externalUniqServerIp = Long.parseLong(String.valueOf(map.get("external_uniq_server_ip") == null ? 0 : map.get("external_uniq_server_ip")));
                HashMap<String, Object> item = Maps.newLinkedHashMap();
                item.put(TrafficSpectrumConstants.INTERNAL_UNIQ_CLIENT_IP, internalUniqClientIp);
                item.put(TrafficSpectrumConstants.INTERNAL_UNIQ_SERVER_IP, internalUniqServerIp);
                item.put(TrafficSpectrumConstants.EXTERNAL_UNIQ_CLIENT_IP, externalUniqClientIp);
                item.put(TrafficSpectrumConstants.EXTERNAL_UNIQ_SERVER_IP, externalUniqServerIp);
                map.clear();
                map.putAll(item);
            });
            return baseResult.getData();
        }
        throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(), baseResult.getMessage()));
    }

    @Override
    public List<Map<String, Object>> topServerIpAndServerDomain(DSLQueryRequestParam dslQueryRequestParam) {
        DSLQueryContext dslQueryContext = BeanUtil.copyProperties(dslQueryRequestParam, DSLQueryContext.class);
        String limit = dslQueryContext.getLimit();
        if (StrUtil.isBlank(limit)) {
            dslQueryContext.setLimit("10");
        }
        String sqlIp = dslQueryContext.toSql(
                environment.getProperty(TRAFFIC_SPECTRUM_APP_DISTRIBUTION_SERVER_IP)
                , TABLE_TRAFFIC_SKETCH_METRIC
                , databaseService.getPartitionKey(TABLE_TRAFFIC_SKETCH_METRIC)
                , TIME_FIELD_TYPE);
        BaseResult<List<Map<String, Object>>> baseResultIp = sqlSyncQueryService.executeQuery(
                SQLQueryContext.builder()
                        .originalSQL(sqlIp)
                        .option(QueryOption.REAL_TIME.getValue())
                        .build());
        String sqlDomain = dslQueryContext.toSql(
                environment.getProperty(TRAFFIC_SPECTRUM_APP_DISTRIBUTION_SERVER_DOMAIN)
                , TABLE_TRAFFIC_SKETCH_METRIC
                , databaseService.getPartitionKey(TABLE_TRAFFIC_SKETCH_METRIC)
                , TIME_FIELD_TYPE);
        BaseResult<List<Map<String, Object>>> baseResultDomain = sqlSyncQueryService.executeQuery(
                SQLQueryContext.builder()
                        .originalSQL(sqlDomain)
                        .option(QueryOption.REAL_TIME.getValue())
                        .build());
        if (baseResultIp.isSuccess() && baseResultDomain.isSuccess()) {
            if ((baseResultIp.getData() == null || baseResultIp.getData().isEmpty()) && (baseResultDomain.getData() == null || baseResultDomain.getData().isEmpty())) {
                return Lists.newArrayList();
            }
            Map<String, Object> map = Maps.newHashMap();
            map.put(TrafficSpectrumConstants.TOP_SERVER_IPS, baseResultIp.getData());
            map.put(TrafficSpectrumConstants.TOP_SERVER_DOMAINS, baseResultDomain.getData());
            return Lists.newArrayList(map);
        }
        throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(), baseResultIp.getMessage() + "; " + baseResultDomain.getMessage()));
    }

    @Override
    public List<Map<String, Object>> internalExternalDimensionMetricsStat(DSLQueryRequestParam dslQueryRequestParam) {
        DSLQueryContext dslQueryContext = BeanUtil.copyProperties(dslQueryRequestParam, DSLQueryContext.class);
        dslQueryContext.setLimit(String.valueOf(engineConfigSource.getTrafficSpectrumClientIPAppResultNum()));
        if (StrUtil.isBlank(dslQueryContext.getGranularity())) {
            dslQueryContext.setGranularity(GRANULARITY_DEFAULT);
        }
        String sql = dslQueryContext.toSql(
                environment.getProperty(TRAFFIC_SPECTRUM_CLIENT_IP_CONNECT_APPLICATION_USAGE)
                , TABLE_TRAFFIC_SKETCH_METRIC
                , databaseService.getPartitionKey(TABLE_TRAFFIC_SKETCH_METRIC)
                , TIME_FIELD_TYPE);
        BaseResult<List<Map<String, Object>>> baseResult = sqlSyncQueryService.executeQuery(
                SQLQueryContext.builder()
                        .originalSQL(sql)
                        .option(QueryOption.REAL_TIME.getValue())
                        .build());
        if (baseResult.isSuccess()) {
            if (baseResult.getData() == null || baseResult.getData().isEmpty()) {
                return Lists.newArrayList();
            }
            List<Map<String, Object>> data = baseResult.getData();
            Map<String, Object> nodes = Maps.newHashMap();
            Map<String, Map<String, Object>> internalNodes = Maps.newHashMap();
            Map<String, Map<String, Object>> externalNodes = Maps.newHashMap();
            List<Map<String, Object>> links = Lists.newArrayList();

            data.forEach(x -> {
                String direction = String.valueOf(x.get("direction"));
                long sessions = Long.parseLong(String.valueOf(x.get("sessions") == null ? 0 : x.get("sessions")));
                long bytes = Long.parseLong(String.valueOf(x.get("bytes") == null ? 0 : x.get("bytes")));
                long packets = Long.parseLong(String.valueOf(x.get("packets") == null ? 0 : x.get("packets")));
                long maxRate = Long.parseLong(String.valueOf(x.get("max_rate") == null ? 0 : x.get("max_rate")));
                long avgRate = Long.parseLong(String.valueOf(x.get("avg_rate") == null ? 0 : x.get("avg_rate")));
                String clientIp = String.valueOf(x.get("client_ip"));
                String app = String.valueOf(x.get("app"));
                String clientCountry = String.valueOf(x.get("client_country"));
                String appCategory = String.valueOf(x.get("app_category"));

                Map<String, Object> internalNode = Maps.newHashMap();
                Map<String, Object> externalNode = Maps.newHashMap();
                String uuidInternal;
                String uuidExternal;
                if (TrafficSpectrumConstants.OUTBOUND.equalsIgnoreCase(direction)) {
                    uuidInternal = UUID.nameUUIDFromBytes(StrUtil.concat(false, TrafficSpectrumConstants.INTERNAL_NODE, String.valueOf(clientIp), String.valueOf(clientCountry), TrafficSpectrumConstants.OBJECT_TYPE_IP).getBytes()).toString();
                    internalNode.put(TrafficSpectrumConstants.ID, uuidInternal);
                    internalNode.put(TrafficSpectrumConstants.OBJECT_TYPE, TrafficSpectrumConstants.OBJECT_TYPE_IP);
                    internalNode.put(TrafficSpectrumConstants.NAME, clientIp);
                    internalNode.put(TrafficSpectrumConstants.CATEGORY, getIpCategory(clientIp, clientCountry));
                    internalNodes.put(uuidInternal, internalNode);

                    uuidExternal = UUID.nameUUIDFromBytes(StrUtil.concat(false, TrafficSpectrumConstants.EXTERNAL_NODE, String.valueOf(app), String.valueOf(appCategory), TrafficSpectrumConstants.OBJECT_TYPE_APPLICATION).getBytes()).toString();
                    externalNode.put(TrafficSpectrumConstants.ID, uuidExternal);
                    externalNode.put(TrafficSpectrumConstants.OBJECT_TYPE, TrafficSpectrumConstants.OBJECT_TYPE_APPLICATION);
                    externalNode.put(TrafficSpectrumConstants.NAME, app);
                    externalNode.put(TrafficSpectrumConstants.CATEGORY, getApplicationCategory(appCategory));
                    externalNodes.put(uuidExternal, externalNode);

                } else if (TrafficSpectrumConstants.INBOUND.equalsIgnoreCase(direction)) {
                    uuidExternal = UUID.nameUUIDFromBytes(StrUtil.concat(false, TrafficSpectrumConstants.EXTERNAL_NODE, String.valueOf(clientIp), String.valueOf(clientCountry), TrafficSpectrumConstants.OBJECT_TYPE_IP).getBytes()).toString();
                    externalNode.put(TrafficSpectrumConstants.ID, uuidExternal);
                    externalNode.put(TrafficSpectrumConstants.OBJECT_TYPE, TrafficSpectrumConstants.OBJECT_TYPE_IP);
                    externalNode.put(TrafficSpectrumConstants.NAME, clientIp);
                    externalNode.put(TrafficSpectrumConstants.CATEGORY, getIpCategory(clientIp, clientCountry));
                    externalNodes.put(uuidExternal, externalNode);

                    uuidInternal = UUID.nameUUIDFromBytes(StrUtil.concat(false, TrafficSpectrumConstants.INTERNAL_NODE, String.valueOf(app), String.valueOf(appCategory), TrafficSpectrumConstants.OBJECT_TYPE_APPLICATION).getBytes()).toString();
                    internalNode.put(TrafficSpectrumConstants.ID, uuidInternal);
                    internalNode.put(TrafficSpectrumConstants.OBJECT_TYPE, TrafficSpectrumConstants.OBJECT_TYPE_APPLICATION);
                    internalNode.put(TrafficSpectrumConstants.NAME, app);
                    internalNode.put(TrafficSpectrumConstants.CATEGORY, getApplicationCategory(appCategory));
                    internalNodes.put(uuidInternal, internalNode);
                } else {
                    log.warn("Unknown direction: {}", direction);
                    return;
                }
                Map<String, Object> link = Maps.newHashMap();
                link.put(TrafficSpectrumConstants.SOURCE, uuidInternal);
                link.put(TrafficSpectrumConstants.TARGET, uuidExternal);
                link.put(TrafficSpectrumConstants.DIRECTION, direction);
                link.put(TrafficSpectrumConstants.SESSIONS, sessions);
                link.put(TrafficSpectrumConstants.BYTES, bytes);
                link.put(TrafficSpectrumConstants.PACKETS, packets);
                link.put(TrafficSpectrumConstants.MAX_RATE, maxRate);
                link.put(TrafficSpectrumConstants.AVG_RATE, avgRate);
                links.add(link);

            });
            nodes.put(TrafficSpectrumConstants.INTERNAL_NODE, new ArrayList<>(internalNodes.values()));
            nodes.put(TrafficSpectrumConstants.EXTERNAL_NODE, new ArrayList<>(externalNodes.values()));
            Map<String, Object> result = Maps.newHashMap();
            result.put(TrafficSpectrumConstants.NODES, nodes);
            result.put(TrafficSpectrumConstants.LINKS, links);
            return Lists.newArrayList(result);
        }
        throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(), baseResult.getMessage()));
    }

    @Override
    public List<Map<String, Object>> networkThroughputTrend(DSLQueryRequestParam dslQueryRequestParam) {
        DSLQueryContext dslQueryContext = BeanUtil.copyProperties(dslQueryRequestParam, DSLQueryContext.class);
        if (StrUtil.isBlank(dslQueryContext.getGranularity())) {
            dslQueryContext.setGranularity(GRANULARITY_DEFAULT);
        }
        String sql = dslQueryContext.toSql(
                environment.getProperty(TRAFFIC_SPECTRUM_NETWORK_THROUGHPUT_TREND)
                , TABLE_TRAFFIC_SKETCH_METRIC
                , databaseService.getPartitionKey(TABLE_TRAFFIC_SKETCH_METRIC)
                , TIME_FIELD_TYPE);
        BaseResult baseResult = sqlSyncQueryService.executeQuery(
                SQLQueryContext.builder()
                        .originalSQL(sql)
                        .option(QueryOption.REAL_TIME.getValue())
                        .build());
        if (baseResult.isSuccess()) {
            return (List<Map<String, Object>>) baseResult.getData();
        }
        throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(), baseResult.getMessage()));
    }

    @Override
    public Map<String, Object> getInternalExternalBipartiteGraph(Map<String, Object> data, Integer clientIPAppResultNum) {
        Map<String, List<Map<String, Object>>> originalNodes = (Map<String, List<Map<String, Object>>>) data.get(TrafficSpectrumConstants.NODES);
        List<Map<String, Object>> originalLinks = (List<Map<String, Object>>) data.get(TrafficSpectrumConstants.LINKS);
        List<Map<String, Object>> originalExternalNodeList = originalNodes.get(TrafficSpectrumConstants.EXTERNAL_NODE);
        List<Map<String, Object>> originalInternalNodeList = originalNodes.get(TrafficSpectrumConstants.INTERNAL_NODE);
        // TODO 自动降序排序基于 bytes
        originalLinks.sort((o1, o2) -> {
            long bytes1 = Long.parseLong(String.valueOf(o1.get(TrafficSpectrumConstants.BYTES)));
            long bytes2 = Long.parseLong(String.valueOf(o2.get(TrafficSpectrumConstants.BYTES)));
            return Long.compare(bytes2, bytes1);
        });
        // TODO step1 原数据拆封 nodes、links
        // TODO 从 internalNodeList 和 externalNodeList 过滤除 name 不为Other的元素列表 和是Other的元素列表
        Map<String, Map<String, Object>> originalInternalOtherMap = Maps.newHashMap();
        Map<String, Map<String, Object>> originalInternalNoOtherMap = Maps.newHashMap();
        for (Map<String, Object> value : originalInternalNodeList) {
            if (StrUtil.equals(String.valueOf(value.get(TrafficSpectrumConstants.NAME)), TrafficSpectrumConstants.OTHER)) {
                originalInternalOtherMap.put(String.valueOf(value.get(TrafficSpectrumConstants.ID)), value);
            } else {
                originalInternalNoOtherMap.put(String.valueOf(value.get(TrafficSpectrumConstants.ID)), value);
            }
        }

        Map<String, Map<String, Object>> originalExternalOtherMap = Maps.newHashMap();
        Map<String, Map<String, Object>> originalExternalNoOtherMap = Maps.newHashMap();
        for (Map<String, Object> value : originalExternalNodeList) {
            if (StrUtil.equals(String.valueOf(value.get(TrafficSpectrumConstants.NAME)), TrafficSpectrumConstants.OTHER)) {
                originalExternalOtherMap.put(String.valueOf(value.get(TrafficSpectrumConstants.ID)), value);
            } else {
                originalExternalNoOtherMap.put(String.valueOf(value.get(TrafficSpectrumConstants.ID)), value);
            }
        }

        List<Map<String, Object>> originalOtherLinks = new ArrayList<>();
        List<Map<String, Object>> originalNoOtherLinks = new ArrayList<>();
        for (Map<String, Object> value : originalLinks) {
            String source = String.valueOf(value.get(TrafficSpectrumConstants.SOURCE));
            String target = String.valueOf(value.get(TrafficSpectrumConstants.TARGET));
            if (originalExternalOtherMap.containsKey(source)
                    || originalExternalOtherMap.containsKey(target)
                    || originalInternalOtherMap.containsKey(source)
                    || originalInternalOtherMap.containsKey(target)) {
                originalOtherLinks.add(value);
            } else {
                originalNoOtherLinks.add(value);
            }
        }

        if (clientIPAppResultNum == null || clientIPAppResultNum >= originalNoOtherLinks.size()) {
            return data;
        }

        // TODO step2 结果封装
        // TODO 生成结果 nods
        Map<String, Map<String, Object>> resultNoOtherInternalNodeMap = Maps.newHashMap();
        Map<String, Map<String, Object>> resultNoOtherExternalNodeMap = Maps.newHashMap();
        Set<String> sourceSets = Sets.newHashSet();
        Set<String> targetSets = Sets.newHashSet();
        for (Map<String, Object> map : originalNoOtherLinks) {
            String source = String.valueOf(map.get(TrafficSpectrumConstants.SOURCE));
            sourceSets.add(source);
            String target = String.valueOf(map.get(TrafficSpectrumConstants.TARGET));
            targetSets.add(target);
            if (sourceSets.size() > clientIPAppResultNum || targetSets.size() > clientIPAppResultNum) {
                continue;
            }
            resultNoOtherInternalNodeMap.put(source, originalInternalNoOtherMap.get(source));
            resultNoOtherExternalNodeMap.put(target, originalExternalNoOtherMap.get(target));
        }
        if (sourceSets.size() <= clientIPAppResultNum || targetSets.size() <= clientIPAppResultNum) {
            return data;
        }

        Map<String, Map<String, Object>> resultOtherInternalNodeMap = Maps.newHashMap();
        Map<String, Map<String, Object>> resultOtherExternalNodeMap = Maps.newHashMap();
        for (Map<String, Object> originalItem : originalInternalNodeList) {
            String originalId = String.valueOf(originalItem.get(TrafficSpectrumConstants.ID));
            if (resultNoOtherInternalNodeMap.containsKey(originalId)) {
                continue;
            }
            Map<String, Object> otherInternalNodeMapItem = Maps.newHashMap();
            String category = String.valueOf(originalItem.get(TrafficSpectrumConstants.CATEGORY));
            String objectType = String.valueOf(originalItem.get(TrafficSpectrumConstants.OBJECT_TYPE));
            otherInternalNodeMapItem.put(TrafficSpectrumConstants.OBJECT_TYPE, objectType);
            boolean presentCategory = resultNoOtherInternalNodeMap.values().stream().anyMatch(o -> objectType.equals(o.get(TrafficSpectrumConstants.OBJECT_TYPE)) && category.equals(o.get(TrafficSpectrumConstants.CATEGORY)));
            if (presentCategory) {
                otherInternalNodeMapItem.put(TrafficSpectrumConstants.CATEGORY, category);
            } else {
                otherInternalNodeMapItem.put(TrafficSpectrumConstants.CATEGORY, TrafficSpectrumConstants.OTHER);
            }
            otherInternalNodeMapItem.put(TrafficSpectrumConstants.NAME, TrafficSpectrumConstants.OTHER);
            String unoriginalId = UUID.nameUUIDFromBytes(StrUtil.concat(false, TrafficSpectrumConstants.INTERNAL_NODE, String.valueOf(otherInternalNodeMapItem.get(TrafficSpectrumConstants.NAME)), String.valueOf(otherInternalNodeMapItem.get(TrafficSpectrumConstants.CATEGORY)), String.valueOf(otherInternalNodeMapItem.get(TrafficSpectrumConstants.OBJECT_TYPE))).getBytes()).toString();
            otherInternalNodeMapItem.put(TrafficSpectrumConstants.ID, unoriginalId);
            resultOtherInternalNodeMap.put(originalId, otherInternalNodeMapItem);
        }

        for (Map<String, Object> originalItem : originalExternalNodeList) {
            String originalId = String.valueOf(originalItem.get(TrafficSpectrumConstants.ID));
            if (resultNoOtherExternalNodeMap.containsKey(originalId)) {
                continue;
            }
            Map<String, Object> otherExternalNodeMapItem = Maps.newHashMap();
            String category = String.valueOf(originalItem.get(TrafficSpectrumConstants.CATEGORY));
            String objectType = String.valueOf(originalItem.get(TrafficSpectrumConstants.OBJECT_TYPE));
            otherExternalNodeMapItem.put(TrafficSpectrumConstants.OBJECT_TYPE, objectType);
            boolean presentCategory = resultNoOtherExternalNodeMap.values().stream().anyMatch(o -> objectType.equals(o.get(TrafficSpectrumConstants.OBJECT_TYPE)) && category.equals(o.get(TrafficSpectrumConstants.CATEGORY)));
            if (!presentCategory) {
                otherExternalNodeMapItem.put(TrafficSpectrumConstants.CATEGORY, TrafficSpectrumConstants.OTHER);
            } else {
                otherExternalNodeMapItem.put(TrafficSpectrumConstants.CATEGORY, category);
            }
            otherExternalNodeMapItem.put(TrafficSpectrumConstants.NAME, TrafficSpectrumConstants.OTHER);
            String unoriginalId = UUID.nameUUIDFromBytes(StrUtil.concat(false, TrafficSpectrumConstants.EXTERNAL_NODE, String.valueOf(otherExternalNodeMapItem.get(TrafficSpectrumConstants.NAME)), String.valueOf(otherExternalNodeMapItem.get(TrafficSpectrumConstants.CATEGORY)), String.valueOf(otherExternalNodeMapItem.get(TrafficSpectrumConstants.OBJECT_TYPE))).getBytes()).toString();
            otherExternalNodeMapItem.put(TrafficSpectrumConstants.ID, unoriginalId);
            resultOtherExternalNodeMap.put(originalId, otherExternalNodeMapItem);
        }

        // TODO 基于 originalLinks 生成结果 resultLinks
        List<Map<String, Object>> resultLinks = Lists.newArrayList(originalLinks);
        for (Map<String, Object> resultLink : resultLinks) {
            String source = String.valueOf(resultLink.get(TrafficSpectrumConstants.SOURCE));
            String target = String.valueOf(resultLink.get(TrafficSpectrumConstants.TARGET));
            if (resultOtherInternalNodeMap.containsKey(source)) {
                resultLink.put(TrafficSpectrumConstants.SOURCE, resultOtherInternalNodeMap.get(source).get(TrafficSpectrumConstants.ID));
            }
            if (resultOtherExternalNodeMap.containsKey(target)) {
                resultLink.put(TrafficSpectrumConstants.TARGET, resultOtherExternalNodeMap.get(target).get(TrafficSpectrumConstants.ID));
            }
        }

        Map<String, Object> result = Maps.newHashMap();
        List<Map<String, Object>> links = mergeLinks(resultLinks);
        Map<String, List<Map<String, Object>>> nodes = Maps.newHashMap();
        List<Map<String, Object>> internalNode = Lists.newArrayList();
        List<Map<String, Object>> externalNode = Lists.newArrayList();
        internalNode.addAll(resultOtherInternalNodeMap.values());
        internalNode.addAll(resultNoOtherInternalNodeMap.values());
        externalNode.addAll(resultOtherExternalNodeMap.values());
        externalNode.addAll(resultNoOtherExternalNodeMap.values());
        nodes.put(TrafficSpectrumConstants.INTERNAL_NODE, internalNode);
        nodes.put(TrafficSpectrumConstants.EXTERNAL_NODE, externalNode);
        Map<String, List<Map<String, Object>>> distinctNodes = distinctNodes(nodes);
        result.put(TrafficSpectrumConstants.NODES, distinctNodes);
        result.put(TrafficSpectrumConstants.LINKS, links);
        return result;
    }

    @Override
    public Map<String, Object> mergeInternalExternalBipartiteGraph(Map<String, Object> data1, Map<String, Object> data2) {
        Map<String, List<Map<String, Object>>> nodes1 = (Map<String, List<Map<String, Object>>>) data1.get(TrafficSpectrumConstants.NODES);
        List<Map<String, Object>> links1 = (List<Map<String, Object>>) data1.get(TrafficSpectrumConstants.LINKS);
        List<Map<String, Object>> externalNodeList1 = nodes1.get(TrafficSpectrumConstants.EXTERNAL_NODE);
        List<Map<String, Object>> internalNodeList1 = nodes1.get(TrafficSpectrumConstants.INTERNAL_NODE);

        Map<String, List<Map<String, Object>>> nodes2 = (Map<String, List<Map<String, Object>>>) data2.get(TrafficSpectrumConstants.NODES);
        List<Map<String, Object>> links2 = (List<Map<String, Object>>) data2.get(TrafficSpectrumConstants.LINKS);
        List<Map<String, Object>> externalNodeList2 = nodes2.get(TrafficSpectrumConstants.EXTERNAL_NODE);
        List<Map<String, Object>> internalNodeList2 = nodes2.get(TrafficSpectrumConstants.INTERNAL_NODE);
        // TODO 合并 data1、data2，并且去重
        List<Map<String, Object>> lists = Lists.newArrayList();
        lists.addAll(links1);
        lists.addAll(links2);
        List<Map<String, Object>> distinctLinks = mergeLinks(lists);

        Map<String, List<Map<String, Object>>> nodes = Maps.newHashMap();
        List<Map<String, Object>> externalNode = Lists.newArrayList();
        externalNode.addAll(externalNodeList1);
        externalNode.addAll(externalNodeList2);
        List<Map<String, Object>> internalNode = Lists.newArrayList();
        internalNode.addAll(internalNodeList1);
        internalNode.addAll(internalNodeList2);
        nodes.put(TrafficSpectrumConstants.INTERNAL_NODE, internalNode);
        nodes.put(TrafficSpectrumConstants.EXTERNAL_NODE, externalNode);
        Map<String, List<Map<String, Object>>> distinctNodes = distinctNodes(nodes);
        Map<String, Object> data = Maps.newHashMap();
        data.put(TrafficSpectrumConstants.NODES, distinctNodes);
        data.put(TrafficSpectrumConstants.LINKS, distinctLinks);
        int sqrt = (int) Math.sqrt(engineConfigSource.getMaxCacheNum());
        return getInternalExternalBipartiteGraph(data, Math.max(sqrt, engineConfigSource.getTrafficSpectrumClientIPAppResultNum()));
    }

    @Override
    public List<Map<String, Object>> mergeLinks(List<Map<String, Object>> links) {
        Map<String, Map<String, Object>> resultLinksMap = Maps.newHashMap();
        links.forEach(o -> {
            String uniqueKey = DigestUtil.md5Hex(o.get(TrafficSpectrumConstants.SOURCE)
                    + TrafficSpectrumConstants.SPLIT_HYPHEN + o.get(TrafficSpectrumConstants.DIRECTION)
                    + TrafficSpectrumConstants.SPLIT_HYPHEN + o.get(TrafficSpectrumConstants.TARGET));
            if (resultLinksMap.containsKey(uniqueKey)) {
                Map<String, Object> map = resultLinksMap.get(uniqueKey);
                map.put(TrafficSpectrumConstants.SESSIONS, Long.parseLong(String.valueOf(map.get(TrafficSpectrumConstants.SESSIONS))) + Long.parseLong(String.valueOf(o.get(TrafficSpectrumConstants.SESSIONS))));
                Long bytes1 = Long.parseLong(String.valueOf(map.get(TrafficSpectrumConstants.BYTES)));
                Long bytes2 = Long.parseLong(String.valueOf(o.get(TrafficSpectrumConstants.BYTES)));
                map.put(TrafficSpectrumConstants.BYTES, bytes1 + bytes2);
                map.put(TrafficSpectrumConstants.PACKETS, Long.parseLong(String.valueOf(map.get(TrafficSpectrumConstants.PACKETS))) + Long.parseLong(String.valueOf(o.get(TrafficSpectrumConstants.PACKETS))));
                map.put(TrafficSpectrumConstants.MAX_RATE, Math.max(Long.parseLong(String.valueOf(map.get(TrafficSpectrumConstants.MAX_RATE))), Long.parseLong(String.valueOf(o.get(TrafficSpectrumConstants.MAX_RATE)))));

                //TODO 计算 avg_rate 时，通过 bytes 权重，计算加权平均值
                Long avgRate1 = Long.parseLong(String.valueOf(map.get(TrafficSpectrumConstants.AVG_RATE)));
                Long avgRate2 = Long.parseLong(String.valueOf(o.get(TrafficSpectrumConstants.AVG_RATE)));
                if (bytes1 + bytes2 == 0) {
                    map.put(TrafficSpectrumConstants.AVG_RATE, (avgRate1 + avgRate2) / 2);
                } else {
                    long avg = (long) (avgRate1 * (bytes1 * 1.0 / (bytes1 + bytes2)) + avgRate2 * (bytes2 * 1.0 / (bytes1 + bytes2)));
                    map.put(TrafficSpectrumConstants.AVG_RATE, avg);
                }
                resultLinksMap.put(uniqueKey, map);
            } else {
                resultLinksMap.put(uniqueKey, o);
            }
        });
        List<Map<String, Object>> result = new ArrayList<>(resultLinksMap.values());
        result.sort((o1, o2) -> {
            long bytes1 = Long.parseLong(String.valueOf(o1.get(TrafficSpectrumConstants.BYTES)));
            long bytes2 = Long.parseLong(String.valueOf(o2.get(TrafficSpectrumConstants.BYTES)));
            return Long.compare(bytes2, bytes1);
        });
        return result;
    }

    @Override
    public Map<String, List<Map<String, Object>>> distinctNodes(Map<String, List<Map<String, Object>>> nodes) {
        Map<String, List<Map<String, Object>>> resultNode = Maps.newHashMap();
        List<Map<String, Object>> internalNodes = nodes.get(TrafficSpectrumConstants.INTERNAL_NODE);
        List<Map<String, Object>> externalNodes = nodes.get(TrafficSpectrumConstants.EXTERNAL_NODE);
        List<Map<String, Object>> resultInternalNodes = internalNodes.stream().collect(Collectors.collectingAndThen(Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(o -> o.get(TrafficSpectrumConstants.ID).toString()))), ArrayList::new));
        List<Map<String, Object>> resultExternalNodes = externalNodes.stream().collect(Collectors.collectingAndThen(Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(o -> o.get(TrafficSpectrumConstants.ID).toString()))), ArrayList::new));
        resultNode.put(TrafficSpectrumConstants.INTERNAL_NODE, resultInternalNodes);
        resultNode.put(TrafficSpectrumConstants.EXTERNAL_NODE, resultExternalNodes);
        return resultNode;
    }

    private String getApplicationCategory(String appCategory) {
        if (StrUtil.isEmpty(appCategory)) {
            return TrafficSpectrumConstants.OTHER;
        }
        return appCategory;
    }

    private String getIpCategory(String clientIp, String clientCountry) {
        if (!StrUtil.isEmpty(clientCountry)) {
            return clientCountry;
        }
        if (StrUtil.isEmpty(clientCountry) && IPUtil.isIPv4Address(clientIp) && IPUtil.internalIp(clientIp)) {
            return TrafficSpectrumConstants.PRIVATE_IP;
        }
        return TrafficSpectrumConstants.OTHER;
    }

    @Autowired
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Autowired
    public void setDatabaseService(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    @Autowired
    public void setSqlSyncQueryService(SQLSyncQueryService sqlSyncQueryService) {
        this.sqlSyncQueryService = sqlSyncQueryService;
    }

    @Autowired
    public void setEngineConfigSource(EngineConfigSource engineConfigSource) {
        this.engineConfigSource = engineConfigSource;
    }
}
