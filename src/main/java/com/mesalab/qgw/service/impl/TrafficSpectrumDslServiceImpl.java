package com.mesalab.qgw.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.geedgenetworks.utils.IPUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

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
    private static final String GRANULARITY_DEFAULT = "PT15S";

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
            map.put(TrafficSpectrumConstants.TOP_SERVER_IP, baseResultIp.getData());
            map.put(TrafficSpectrumConstants.TOP_SERVER_DOMAIN, baseResultDomain.getData());
            return Lists.newArrayList(map);
        }
        throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(), baseResultIp.getMessage() + "; " + baseResultDomain.getMessage()));
    }

    @Override
    public List<Map<String, Object>> internalExternalDimensionMetricsStat(DSLQueryRequestParam dslQueryRequestParam) {
        DSLQueryContext dslQueryContext = BeanUtil.copyProperties(dslQueryRequestParam, DSLQueryContext.class);
        if (StrUtil.isBlank(dslQueryContext.getLimit())) {
            dslQueryContext.setLimit(String.valueOf(engineConfigSource.getTrafficSpectrumCIpConnectAppTopK()));
        }
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
                    uuidInternal = UUID.nameUUIDFromBytes(StrUtil.concat(false, String.valueOf(clientIp), TrafficSpectrumConstants.OBJECT_TYPE_IP).getBytes()).toString();
                    internalNode.put(TrafficSpectrumConstants.ID, uuidInternal);
                    internalNode.put(TrafficSpectrumConstants.OBJECT_TYPE, TrafficSpectrumConstants.OBJECT_TYPE_IP);
                    internalNode.put(TrafficSpectrumConstants.NAME, clientIp);
                    internalNode.put(TrafficSpectrumConstants.CATEGORY, getIpCategory(clientIp, clientCountry));
                    internalNodes.put(uuidInternal, internalNode);

                    uuidExternal = UUID.nameUUIDFromBytes(StrUtil.concat(false, String.valueOf(app), String.valueOf(appCategory), TrafficSpectrumConstants.OBJECT_TYPE_APPLICATION).getBytes()).toString();
                    externalNode.put(TrafficSpectrumConstants.ID, uuidExternal);
                    externalNode.put(TrafficSpectrumConstants.OBJECT_TYPE, TrafficSpectrumConstants.OBJECT_TYPE_APPLICATION);
                    externalNode.put(TrafficSpectrumConstants.NAME, app);
                    externalNode.put(TrafficSpectrumConstants.CATEGORY, getApplicationCategory(appCategory));
                    externalNodes.put(uuidExternal, externalNode);

                } else if (TrafficSpectrumConstants.INBOUND.equalsIgnoreCase(direction)) {
                    uuidExternal = UUID.nameUUIDFromBytes(StrUtil.concat(false, String.valueOf(clientIp), String.valueOf(clientCountry), TrafficSpectrumConstants.OBJECT_TYPE_IP).getBytes()).toString();
                    externalNode.put("id", uuidExternal);
                    externalNode.put(TrafficSpectrumConstants.OBJECT_TYPE, TrafficSpectrumConstants.OBJECT_TYPE_IP);
                    externalNode.put(TrafficSpectrumConstants.NAME, clientIp);
                    externalNode.put(TrafficSpectrumConstants.CATEGORY, getIpCategory(clientIp, clientCountry));
                    externalNodes.put(uuidExternal, externalNode);

                    uuidInternal = UUID.nameUUIDFromBytes(StrUtil.concat(false, String.valueOf(app), String.valueOf(appCategory), TrafficSpectrumConstants.OBJECT_TYPE_APPLICATION).getBytes()).toString();
                    internalNode.put(TrafficSpectrumConstants.ID, uuidInternal);
                    internalNode.put(TrafficSpectrumConstants.OBJECT_TYPE, TrafficSpectrumConstants.OBJECT_TYPE_APPLICATION);
                    internalNode.put(TrafficSpectrumConstants.NAME, app);
                    internalNode.put(TrafficSpectrumConstants.CATEGORY, getApplicationCategory(appCategory));
                    internalNodes.put(uuidInternal, internalNode);
                } else {
                    log.info("Unknown direction: {}", direction);
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
            nodes.put(TrafficSpectrumConstants.INTERNAL_NODE, internalNodes.values());
            nodes.put(TrafficSpectrumConstants.EXTERNAL_NODE, externalNodes.values());
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
