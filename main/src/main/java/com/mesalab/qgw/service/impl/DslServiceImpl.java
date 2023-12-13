package com.mesalab.qgw.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.geedgenetworks.utils.StringUtil;
import com.google.common.base.Splitter;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mesalab.cn.enums.RangeTypeEnum;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.OutputModeEnum;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.TreeUtils;
import com.mesalab.knowledge.entity.*;
import com.mesalab.knowledge.enums.MatchEnum;
import com.mesalab.knowledge.enums.SortEnum;
import com.mesalab.knowledge.service.KnowledgeService;
import com.mesalab.network.common.Constants;
import com.mesalab.network.model.protocol.ProtocolTree;
import com.mesalab.qgw.constant.DslIdentifierNameConst;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.qgw.model.basic.ClickHouseHttpSource;
import com.mesalab.qgw.model.basic.DSLProfile;
import com.mesalab.qgw.model.basic.QueryProfile;
import com.mesalab.qgw.service.DslService;
import com.mesalab.qgw.service.DatabaseService;
import com.mesalab.qgw.service.QueryService;
import com.mesalab.services.common.dsl.ComDSLObject;
import com.mesalab.services.common.enums.EntityQueryType;
import com.mesalab.services.common.enums.UnstructuredDataType;
import com.mesalab.services.common.property.SqlPropertySourceFactory;
import com.mesalab.services.service.RelationService;
import com.mesalab.services.service.UnstructuredService;
import com.mesalab.services.service.impl.UnstructuredServiceImpl;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
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
    DatabaseService databaseService;
    @Autowired
    QueryService queryService;
    @Autowired
    UnstructuredService unstructuredService;
    @Autowired
    private ClickHouseHttpSource clickHouseHttpSource;
    @Autowired
    KnowledgeService knowledgeService;
    @Autowired
    RelationService relationService;

    private static final String TABLE_TRAFFIC_GENERAL_STAT = "traffic_general_stat";
    private static final String TABLE_APPLICATION_PROTOCOL_STAT = "application_protocol_stat";
    private static final String TABLE_SESSION_RECORD = "session_record";
    private static final String TABLE_RELATION_ACCOUNT_FRAMEDIP = "relation_account_framedip";
    private static final String TABLE_GTPC_KNOWLEDGE_BASE = "gtpc_knowledge_base";
    private static final String LOGICAL_TYPE_TIMESTAMP = "timestamp";
    private static final String LOGICAL_TYPE_UNIX_TIMESTAMP = "unix_timestamp";

    @Override
    public BaseResult execDsl(@RequestBody DSLProfile dslProfile) {
        log.info("HTTP REST DSL, params is: {}", dslProfile);
        if (DslIdentifierNameConst.APPLICATION_AND_PROTOCOL_SUMMARY.equals(dslProfile.getName())) {
            return appAndProtocolSummary(dslProfile);
        } else if (DslIdentifierNameConst.APPLICATION_AND_PROTOCOL_TREE_COMPOSITION.equals(dslProfile.getName())) {
            return appAndProtocolTreeComposition(dslProfile);
        } else if (DslIdentifierNameConst.APPLICATION_AND_PROTOCOL_TREE_THROUGHPUT.equals(dslProfile.getName())) {
            return applicationAndProtocolTreeThroughput(dslProfile);
        } else if (DslIdentifierNameConst.APPLICATION_AND_PROTOCOL_TOP_APPS.equals(dslProfile.getName())) {
            return applicationAndProtocolTopApp(dslProfile);
        } else if (DslIdentifierNameConst.APPLICATION_AND_PROTOCOL_APP_RELATED_INTERNAL_IPS.equals(dslProfile.getName())) {
            return applicationAndProtocolAppRelatedInternalIps(dslProfile);
        } else if (DslIdentifierNameConst.APPLICATION_AND_PROTOCOL_APP_THROUGHPUT.equals(dslProfile.getName())) {
            return applicationAndProtocolAppThroughput(dslProfile);
        } else if (DslIdentifierNameConst.APPLICATION_AND_PROTOCOL_APP_SUMMARY.equals(dslProfile.getName())) {
            return applicationAndProtocolAppSummary(dslProfile);
        } else if (DslIdentifierNameConst.REAL_TIME_DATA_ANALYTICS_SUBSCRIBER_ID_RELATE_IP.equals(dslProfile.getName())) {
            return realTimeDataAnalyticsSubscriberIdRelateIp(dslProfile);
        } else if (DslIdentifierNameConst.REAL_TIME_DATA_ANALYTICS_MOBILE_IDENTITY_RELATE_TEID.equals(dslProfile.getName())) {
            return realTimeDataAnalyticsMobileIdentityRelateTeid(dslProfile);
        } else if (DslIdentifierNameConst.REAL_TIME_DATA_ANALYTICS_UNSTRUCTURED_DATA.equals(dslProfile.getName())) {
            return realTimeDataAnalyticsUnstructuredData(dslProfile);
        } else if (DslIdentifierNameConst.IP_LEARNING_FQDN_RELATE_IP.equals(dslProfile.getName())) {
            return ipLearningFqdnRelateIp(dslProfile);
        } else if (DslIdentifierNameConst.IP_LEARNING_ACTIVE_IP.equals(dslProfile.getName())) {
            return ipLearningActiveIp(dslProfile);
        }
        throw new BusinessException(ResultStatusEnum.NOT_FOUND.getCode(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(), dslProfile.getName()));
    }

    @Override
    public BaseResult appAndProtocolSummary(DSLProfile dslProfile) {
        BaseResult asymmetricFlowsResult = queryService.executeQuery(
                QueryProfile.builder().query(dslProfile.toSql(environment.getProperty("APPLICATION_AND_PROTOCOL_SUMMARY_ASYMMETRIC_FLOWS_STAT"), TABLE_TRAFFIC_GENERAL_STAT, databaseService.getPartitionKey(TABLE_TRAFFIC_GENERAL_STAT), LOGICAL_TYPE_TIMESTAMP)).build());
        if (!asymmetricFlowsResult.isSuccess()) {
            throw new BusinessException(asymmetricFlowsResult.getStatus(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(), asymmetricFlowsResult.getMessage()));
        }

        BaseResult networkTrafficStatResult = queryService.executeQuery(
                QueryProfile.builder().query(dslProfile.toSql(environment.getProperty("APPLICATION_AND_PROTOCOL_SUMMARY_TRAFFIC_STAT"), TABLE_APPLICATION_PROTOCOL_STAT, databaseService.getPartitionKey(TABLE_APPLICATION_PROTOCOL_STAT), LOGICAL_TYPE_TIMESTAMP)).build());
        if (!networkTrafficStatResult.isSuccess()) {
            throw new BusinessException(networkTrafficStatResult.getStatus(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(), networkTrafficStatResult.getMessage()));
        }

        BaseResult networkUniqueClientIpStatResult = queryService.executeQuery(
                QueryProfile.builder().query(dslProfile.toSql(environment.getProperty("APPLICATION_AND_PROTOCOL_SUMMARY_UNIQUE_CLIENT_IP_STAT"), TABLE_APPLICATION_PROTOCOL_STAT, databaseService.getPartitionKey(TABLE_APPLICATION_PROTOCOL_STAT), LOGICAL_TYPE_TIMESTAMP)).build());
        if (!networkUniqueClientIpStatResult.isSuccess()) {
            throw new BusinessException(networkUniqueClientIpStatResult.getStatus(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(), networkUniqueClientIpStatResult.getMessage()));
        }

        BaseResult networkTcpStatResult = queryService.executeQuery(
                QueryProfile.builder().query(dslProfile.toSql(environment.getProperty("APPLICATION_AND_PROTOCOL_SUMMARY_TCP_STAT"), TABLE_APPLICATION_PROTOCOL_STAT, databaseService.getPartitionKey(TABLE_APPLICATION_PROTOCOL_STAT), LOGICAL_TYPE_TIMESTAMP)).build());
        if (!networkTcpStatResult.isSuccess()) {
            throw new BusinessException(networkTcpStatResult.getStatus(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(), networkTcpStatResult.getMessage()));
        }

        BaseResult networkAppStatResult = queryService.executeQuery(
                QueryProfile.builder().query(dslProfile.toSql(environment.getProperty("APPLICATION_AND_PROTOCOL_SUMMARY_APP_STAT"), TABLE_APPLICATION_PROTOCOL_STAT, databaseService.getPartitionKey(TABLE_APPLICATION_PROTOCOL_STAT), LOGICAL_TYPE_TIMESTAMP)).build());
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
        String sql = dslProfile.toSql(environment.getProperty("APPLICATION_AND_PROTOCOL_TREE_COMPOSITION"), TABLE_APPLICATION_PROTOCOL_STAT, databaseService.getPartitionKey(TABLE_APPLICATION_PROTOCOL_STAT), LOGICAL_TYPE_TIMESTAMP);
        BaseResult baseResult = queryService.executeQuery(queryBuild(sql));
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
        String sql = dslProfile.toSql(environment.getProperty("APPLICATION_AND_PROTOCOL_TREE_THROUGHPUT"), TABLE_APPLICATION_PROTOCOL_STAT, databaseService.getPartitionKey(TABLE_APPLICATION_PROTOCOL_STAT), LOGICAL_TYPE_TIMESTAMP);
        BaseResult baseResult = queryService.executeQuery(queryBuild(sql));
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
        String sql = dslProfile.toSql(environment.getProperty("APPLICATION_AND_PROTOCOL_TOP_APPS"), TABLE_APPLICATION_PROTOCOL_STAT, databaseService.getPartitionKey(TABLE_APPLICATION_PROTOCOL_STAT), LOGICAL_TYPE_TIMESTAMP);
        BaseResult baseResult = queryService.executeQuery(queryBuild(sql));
        if (!baseResult.isSuccess()) {
            throw new BusinessException(baseResult.getStatus(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(), baseResult.getMessage()));
        }
        return baseResult;
    }

    @Override
    public BaseResult applicationAndProtocolAppRelatedInternalIps(DSLProfile dslProfile) {
        String sql = dslProfile.toSql(environment.getProperty("APPLICATION_AND_PROTOCOL_APP_RELATED_INTERNAL_IPS"), TABLE_SESSION_RECORD, databaseService.getPartitionKey(TABLE_SESSION_RECORD), LOGICAL_TYPE_UNIX_TIMESTAMP);
        BaseResult baseResult = queryService.executeQuery(queryBuild(sql));
        if (!baseResult.isSuccess()) {
            throw new BusinessException(baseResult.getStatus(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(), baseResult.getMessage()));
        }
        return baseResult;
    }

    @Override
    public BaseResult applicationAndProtocolAppThroughput(DSLProfile dslProfile) {
        String sql = dslProfile.toSql(environment.getProperty("APPLICATION_AND_PROTOCOL_APP_THROUGHPUT"), TABLE_APPLICATION_PROTOCOL_STAT, databaseService.getPartitionKey(TABLE_APPLICATION_PROTOCOL_STAT), LOGICAL_TYPE_TIMESTAMP);
        BaseResult baseResult = queryService.executeQuery(queryBuild(sql));
        if (!baseResult.isSuccess()) {
            throw new BusinessException(baseResult.getStatus(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(), baseResult.getMessage()));
        }
        return baseResult;
    }

    @Override
    public BaseResult applicationAndProtocolAppSummary(DSLProfile dslProfile) {
        String sql = dslProfile.toSql(environment.getProperty("APPLICATION_AND_PROTOCOL_APP_SUMMARY"), TABLE_APPLICATION_PROTOCOL_STAT, databaseService.getPartitionKey(TABLE_APPLICATION_PROTOCOL_STAT), LOGICAL_TYPE_TIMESTAMP);
        BaseResult baseResult = queryService.executeQuery(queryBuild(sql));
        if (!baseResult.isSuccess()) {
            throw new BusinessException(baseResult.getStatus(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(), baseResult.getMessage()));
        }
        return baseResult;
    }

    @Override
    public BaseResult realTimeDataAnalyticsSubscriberIdRelateIp(DSLProfile dslProfile) {
        String filter = dslProfile.getFilter();
        if (StrUtil.isNotEmpty(filter) && filter.contains("subscriber_id")) {
            try {
                dslProfile.setFilter(inToLike(filter));
            } catch (JSQLParserException e) {
                log.error("InExpression to LikeExpression error: {}", e.getMessage());
                throw new BusinessException(ResultStatusEnum.SERVER_ERROR.getCode(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(), e.getMessage()));
            }
        }
        String sql = dslProfile.toSql(environment.getProperty("REAL_TIME_DATA_ANALYTICS_SUBSCRIBER_ID_RELATE_IP"), TABLE_RELATION_ACCOUNT_FRAMEDIP, "last_update_time", LOGICAL_TYPE_UNIX_TIMESTAMP);
        BaseResult baseResult = queryService.executeQuery(queryBuild(sql));
        if (!baseResult.isSuccess()) {
            throw new BusinessException(baseResult.getStatus(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(), baseResult.getMessage()));
        }
        return baseResult;
    }

    @Override
    public BaseResult realTimeDataAnalyticsMobileIdentityRelateTeid(DSLProfile dslProfile) {
        String dataSource = "gtpc_knowledge_base";
        DSLObject dslObject = getDslObject(dslProfile, null, dataSource);
        ComDSLObject comDSLObject = BeanUtil.copyProperties(dslObject, ComDSLObject.class);
        if (!StrUtil.isEmptyIfStr(dslObject.getQuery())
                && !StrUtil.isEmptyIfStr(dslObject.getQuery().getParameters())
                && !StrUtil.isEmptyIfStr(dslObject.getQuery().getParameters().getLimit())) {
            comDSLObject.getQuery().setLimit(dslObject.getQuery().getParameters().getLimit());
        }
        BaseResult baseResult = relationService.getRealRelation(EntityQueryType.GTPC.getType(), comDSLObject);
        if (!baseResult.isSuccess()) {
            throw new BusinessException(baseResult.getStatus(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(), baseResult.getMessage()));
        }
        return baseResult;
    }

    @Override
    public BaseResult realTimeDataAnalyticsUnstructuredData(DSLProfile dslProfile) {
        Stopwatch watch = Stopwatch.createStarted();
        List<String> fileTypes = Lists.newArrayList();
        List<String> dataSources = Lists.newArrayList();

        ExpressionVisitorAdapter expressionVisitorAdapter = new ExpressionVisitorAdapter() {
            @Override
            public void visit(EqualsTo equalsTo) {
                if (equalsTo.getLeftExpression() != null && "file_type".equals(equalsTo.getLeftExpression().toString())) {
                    if (equalsTo.getRightExpression() instanceof StringValue) {
                        fileTypes.add(((StringValue) equalsTo.getRightExpression()).getValue());
                        equalsTo.setLeftExpression(new LongValue(1));
                        equalsTo.setRightExpression(new LongValue(1));
                    }
                } else if (equalsTo.getLeftExpression() != null && "data_source".equals(equalsTo.getLeftExpression().toString())) {
                    if (equalsTo.getRightExpression() instanceof StringValue) {
                        dataSources.add(((StringValue) equalsTo.getRightExpression()).getValue());
                        equalsTo.setLeftExpression(new LongValue(1));
                        equalsTo.setRightExpression(new LongValue(1));
                    }
                }
            }
        };
        String filter = dslProfile.getFilter();
        Expression expression = null;
        try {
            expression = CCJSqlParserUtil.parseExpression(filter, false);
        } catch (JSQLParserException e) {
            throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(), e.getMessage()));
        }
        expression.accept(expressionVisitorAdapter);
        if (fileTypes.size() != 1 && dataSources.size() != 1) {
            throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(), String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), dslProfile.getFilter()));
        }
        dslProfile.setFilter(expression.toString());

        String type = fileTypes.get(0);
        String dataSource = dataSources.get(0);
        Map<String, Object> fileTypeInSchema = unstructuredService.getUnstructuredFields();
        List<Map> dataList = Lists.newArrayList();
        if (!fileTypeInSchema.containsKey(dataSource) && !clickHouseHttpSource.getDbName().equals(dataSource)) {
            return build(dataList, watch);
        }
        if (UnstructuredDataType.ALL.getType().equalsIgnoreCase(type)) {
            if (clickHouseHttpSource.getDbName().equals(dataSource)) {
                for (String tableName : fileTypeInSchema.keySet()) {
                    Map<String, List<String>> typeFields = (Map<String, List<String>>) fileTypeInSchema.get(tableName);
                    for (String typeTmp : typeFields.keySet()) {
                        dataList.addAll(getData(typeFields.get(typeTmp), tableName, dslProfile, typeTmp));
                    }
                }
            } else {
                Map<String, List<String>> typeFields = (Map<String, List<String>>) fileTypeInSchema.get(dataSource);
                for (String typeTmp : typeFields.keySet()) {
                    dataList.addAll(getData(typeFields.get(typeTmp), dataSource, dslProfile, typeTmp));
                }
            }
        } else {
            if (clickHouseHttpSource.getDbName().equals(dataSource)) {
                for (String tableName : fileTypeInSchema.keySet()) {
                    Map<String, List<String>> typeFields = (Map<String, List<String>>) fileTypeInSchema.get(tableName);
                    dataList.addAll(getData(typeFields.get(type), tableName, dslProfile, type));
                }
            } else {
                Map<String, List<String>> typeFields = (Map<String, List<String>>) fileTypeInSchema.get(dataSource);
                dataList = getData(typeFields.get(type), dataSource, dslProfile, type);
            }
        }
        List<Map> distinctList = dataList.stream()
                .filter(distinctByValue(x -> x.get("file_path")))
                .collect(Collectors.toList());
        if (StrUtil.isNotEmpty(dslProfile.getLimit())) {
            distinctList = distinctList.size() > Long.parseLong(dslProfile.getLimit()) ? distinctList.subList(0, Integer.parseInt(dslProfile.getLimit())) : distinctList;
        }
        return build(Lists.newArrayList(distinctList), watch);
    }

    @Override
    public BaseResult ipLearningFqdnRelateIp(DSLProfile dslProfile) {
        String queryType = "iplearning";
        String dataSource = "IP_LEARNING_VIEW";
        DSLObject dslObject = getDslObject(dslProfile, queryType, dataSource);
        log.info("DSLProfile parse to DSLObject: {}", JSON.toJSONString(dslObject));
        return knowledgeService.query(dslObject);
    }

    @Override
    public BaseResult ipLearningActiveIp(DSLProfile dslProfile) {
        String queryType = "ippool";
        String dataSource = "IP_VIEW";
        DSLObject dslObject = getDslObject(dslProfile, queryType, dataSource);
        log.info("DSLProfile parse to DSLObject: {}", JSON.toJSONString(dslObject));
        return knowledgeService.query(dslObject);
    }

    private static DSLObject getDslObject(DSLProfile dslProfile, String queryType, String dataSource) {
        Parameters parameters = new Parameters();
        List<String> intervals = dslProfile.getIntervals();
        if (!CollectionUtils.isEmpty(intervals)) {
            parameters.setIntervals(intervals);
        }
        String limit = dslProfile.getLimit();
        if (StrUtil.isNotEmpty(limit)) {
            parameters.setLimit(limit);
        }
        List<Match> matches = Lists.newArrayList();
        List<Range> ranges = Lists.newArrayList();
        ExpressionVisitorAdapter expressionVisitorAdapter = getExpressionVisitorAdapter(matches, ranges);
        String filter = dslProfile.getFilter();
        Expression expression = null;
        try {
            expression = CCJSqlParserUtil.parseExpression(filter, false);
        } catch (JSQLParserException e) {
            throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(), e.getMessage()));
        }
        expression.accept(expressionVisitorAdapter);
        Iterator<Range> iterator = ranges.iterator();
        while (iterator.hasNext()) {
            Range next = iterator.next();
            if (next.getFieldKey().equals("1") && next.getType().equals(RangeTypeEnum.EQ.getType())) {
                iterator.remove();
            }
        }
        parameters.setMatch(matches);
        parameters.setRange(ranges);

        List<Sort> sorts = Lists.newArrayList();
        if (StrUtil.isNotEmpty(dslProfile.getOrderBy())) {
            buildSorts(dslProfile.getOrderBy(), sorts);
        }
        parameters.setSort(sorts);
        DSLObject dslObject = new DSLObject();
        DSLObject.DSLQuery dslQuery = new DSLObject.DSLQuery();
        dslQuery.setQueryType(queryType);
        dslQuery.setDataSource(dataSource);
        dslQuery.setParameters(parameters);
        dslObject.setQuery(dslQuery);

        return dslObject;
    }

    private static void buildSorts(String orderByStr, List<Sort> sorts) {
        try {
            Statement parse = CCJSqlParserUtil.parse("select 1 from table order by " + orderByStr);
            if (parse instanceof Select && ((Select) parse).getSelectBody() instanceof PlainSelect) {
                List<OrderByElement> orderByElements = ((PlainSelect) ((Select) parse).getSelectBody()).getOrderByElements();
                for (int i = 0; i < orderByElements.size(); i++) {
                    OrderByElement orderByElement = orderByElements.get(i);
                    Sort sort = new Sort();
                    sort.setFieldKey(orderByElement.getExpression().toString());
                    sort.setType(orderByElement.isAsc() ? SortEnum.ASC.getType() : SortEnum.DESC.getType());
                    sorts.add(sort);
                }
            }
        } catch (JSQLParserException e) {
            throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(), e.getMessage()));
        }
    }

    private static ExpressionVisitorAdapter getExpressionVisitorAdapter(List<Match> matches, List<Range> ranges) {
        ExpressionVisitorAdapter expressionVisitorAdapter = new ExpressionVisitorAdapter() {
            @Override
            public void visit(LikeExpression expr) {
                String left = String.valueOf(expr.getLeftExpression());
                String right = ((StringValue) expr.getRightExpression()).getValue();
                String type = "";
                if (right.startsWith("%") && right.endsWith("%")) {
                    type = MatchEnum.SUBSTRING.getType();
                    right = right.substring(1, right.length() - 1);
                } else if (right.startsWith("%")) {
                    type = MatchEnum.SUFFIX.getType();
                    right = right.substring(1);
                } else if (right.endsWith("%")) {
                    type = MatchEnum.PREFIX.getType();
                    right = right.substring(0, right.length() - 1);
                } else {
                    type = MatchEnum.EXACTLY.getType();
                }

                String finalType = type;
                if (matches.stream().anyMatch(o -> o.getFieldKey().equals(left) && o.getType().equals(finalType))) {
                    for (Match match : matches) {
                        if (match.getFieldKey().equals(left) && match.getType().equals(type)) {
                            match.getFieldValues().add(right);
                        }
                    }
                } else {
                    Match match = new Match();
                    match.setFieldKey(left);
                    match.setType(type);
                    match.setFieldValues(Lists.newArrayList(right));
                    matches.add(match);
                }
            }

            @Override
            public void visit(EqualsTo expr) {
                String left = String.valueOf(expr.getLeftExpression());
                Object right;
                if (expr.getRightExpression() instanceof LongValue) {
                    right = ((LongValue) expr.getRightExpression()).getValue();
                } else {
                    right = ((StringValue) expr.getRightExpression()).getValue();
                }
                if (ranges.stream().anyMatch(o -> o.getFieldKey().equals(left) && o.getType().equals(RangeTypeEnum.EQ.getType()))) {
                    for (Range range : ranges) {
                        if (range.getFieldKey().equals(left) && range.getType().equals(RangeTypeEnum.EQ.getType())) {
                            range.getFieldValues().add(right);
                        }
                    }
                } else {
                    Range range = new Range();
                    range.setFieldKey(left);
                    range.setType(RangeTypeEnum.EQ.getType());
                    range.setFieldValues(Lists.newArrayList(right));
                    ranges.add(range);
                }
            }

            @Override
            public void visit(InExpression expr) {
                String left = String.valueOf(expr.getLeftExpression());
                if (ranges.stream().anyMatch(o -> o.getFieldKey().equals(left) && o.getType().equals(RangeTypeEnum.EQ.getType()))) {
                    for (Range range : ranges) {
                        if (range.getFieldKey().equals(left) && range.getType().equals(RangeTypeEnum.EQ.getType())) {
                            ItemsList rightItemsList = expr.getRightItemsList();
                            rightItemsList.accept(new ItemsListVisitorAdapter() {
                                @Override
                                public void visit(ExpressionList expressionList) {
                                    for (Expression expression : expressionList.getExpressions()) {
                                        if (expression instanceof StringValue) {
                                            range.getFieldValues().add(((StringValue) expression).getValue());
                                        } else if (expression instanceof LongValue) {
                                            range.getFieldValues().add(((LongValue) expression).getValue());
                                        }

                                    }
                                }

                            });
                        }
                    }
                } else {
                    Range range = new Range();
                    range.setFieldKey(left);
                    range.setFieldValues(Lists.newArrayList());
                    range.setType(RangeTypeEnum.EQ.getType());
                    ItemsList rightItemsList = expr.getRightItemsList();
                    rightItemsList.accept(new ItemsListVisitorAdapter() {
                        @Override
                        public void visit(ExpressionList expressionList) {
                            for (Expression expression : expressionList.getExpressions()) {
                                if (expression instanceof StringValue) {
                                    range.getFieldValues().add(((StringValue) expression).getValue());
                                } else if (expression instanceof LongValue) {
                                    range.getFieldValues().add(((LongValue) expression).getValue());
                                }

                            }
                        }
                    });
                    ranges.add(range);
                }
            }

            @Override
            public void visit(GreaterThan expr) {
                String left = String.valueOf(expr.getLeftExpression());
                Object right;
                if (expr.getRightExpression() instanceof LongValue) {
                    right = ((LongValue) expr.getRightExpression()).getValue();
                } else {
                    right = ((StringValue) expr.getRightExpression()).getValue();
                }
                if (ranges.stream().anyMatch(o -> o.getFieldKey().equals(left) && o.getType().equals(RangeTypeEnum.GT.getType()))) {
                    for (Range range : ranges) {
                        if (range.getFieldKey().equals(left) && range.getType().equals(RangeTypeEnum.EQ.getType())) {
                            range.getFieldValues().add(right);
                        }
                    }
                } else {
                    Range range = new Range();
                    range.setFieldKey(left);
                    range.setType(RangeTypeEnum.GT.getType());
                    range.setFieldValues(Lists.newArrayList(right));
                    ranges.add(range);
                }
            }

            @Override
            public void visit(GreaterThanEquals expr) {
                String left = String.valueOf(expr.getLeftExpression());
                Object right;
                if (expr.getRightExpression() instanceof LongValue) {
                    right = ((LongValue) expr.getRightExpression()).getValue();
                } else {
                    right = ((StringValue) expr.getRightExpression()).getValue();
                }
                if (ranges.stream().anyMatch(o -> o.getFieldKey().equals(left) && o.getType().equals(RangeTypeEnum.EQ.getType()))) {
                    for (Range range : ranges) {
                        if (range.getFieldKey().equals(left) && range.getType().equals(RangeTypeEnum.EQ.getType())) {
                            range.getFieldValues().add(right);
                        }
                    }
                } else {
                    Range range = new Range();
                    range.setFieldKey(left);
                    range.setType(RangeTypeEnum.EQ.getType());
                    range.setFieldValues(Lists.newArrayList(right));
                    ranges.add(range);
                }
            }
        };
        return expressionVisitorAdapter;
    }

    private static <T> Predicate<T> distinctByValue(Function<? super T, Object> keyExtractor) {
        Map<Object, Boolean> seen = new ConcurrentHashMap<>();
        return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
    }

    private List<Map> getData(List<String> fields, String dataSource, DSLProfile dslProfile, String type) {
        List<Map> dataResult = Lists.newArrayList();
        for (String field : fields) {
            String sql = dslProfile.toSql(environment.getProperty("REAL_TIME_DATA_ANALYTICS_UNSTRUCTURED_DATA"), dataSource, databaseService.getPartitionKey(dataSource), LOGICAL_TYPE_UNIX_TIMESTAMP);
            sql = sql.replace("$field", field);
            sql = sql.replace("$primary_key", databaseService.getValueByKeyInSchemaDoc(dataSource, "primary_key"));
            sql = sql.replace("$partition_key", databaseService.getValueByKeyInSchemaDoc(dataSource, "partition_key"));
            BaseResult result = queryService.executeQuery(queryBuild(sql));
            if (!result.isSuccess()) {
                log.error("Query unstructured data status error: {}", result.getMessage());
                throw new QGWBusinessException(ResultStatusEnum.SERVER_ERROR.getCode(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), "Query unstructured data status error: " + result.getMessage());
            }
            List<Map> datalist = (List<Map>) result.getData();
            for (Map data : datalist) {
                String fileTypeStr = StringUtil.isNotEmpty(UnstructuredServiceImpl.fileType.get(field)) ? UnstructuredServiceImpl.fileType.get(field).toString() : UnstructuredServiceImpl.OTHER_FILE;
                data.put("log_type", dataSource);
                data.put("file_type", fileTypeStr);
                dataResult.add(data);
            }
        }
        return dataResult;
    }

    private QueryProfile queryBuild(String sql) {
        return QueryProfile.builder().format(OutputModeEnum.JSON.getValue()).query(sql).build();
    }

    private BaseResult build(List<Map> dataList, Stopwatch watch) {
        BaseResult baseResult;
        Map statistics = Maps.newHashMap();
        statistics.put("elapsed", watch.elapsed(TimeUnit.MILLISECONDS));
        statistics.put("result_rows", dataList.size());
        statistics.put("result_bytes", dataList.toString().getBytes().length);
        baseResult = BaseResultGenerator.success("ok", dataList, statistics);
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

    private String inToLike(String filter) throws JSQLParserException {
        List<String> params = Lists.newArrayList();
        List<String> oldExpression = Lists.newArrayList();
        ExpressionVisitorAdapter expressionVisitorAdapter = new ExpressionVisitorAdapter() {
            @Override
            public void visit(InExpression inExpression) {
                if (inExpression.getLeftExpression() != null && "subscriber_id".equals(inExpression.getLeftExpression().toString())) {
                    oldExpression.add(inExpression.toString());
                    ItemsList rightItemsList = inExpression.getRightItemsList();
                    if (rightItemsList instanceof ExpressionList) {
                        ExpressionList items = (ExpressionList) rightItemsList;
                        List<Expression> expressions = items.getExpressions();
                        for (Expression expression : expressions) {
                            if (expression instanceof StringValue) {
                                params.add(DigestUtil.md5Hex(((StringValue) expression).getValue()).concat("|%"));
                            }

                        }
                    }
                }
            }
        };
        Expression expression = CCJSqlParserUtil.parseExpression(filter, false);
        String strExpr = expression.toString();
        expression.accept(expressionVisitorAdapter);
        if (params.size() > 0 && oldExpression.size() == 1) {
            return strExpr.replace(oldExpression.get(0), "( ROWKEY LIKE '" + StrUtil.join("' OR ROWKEY LIKE '", params) + "')");
        }
        return filter;
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
