package com.mesalab.qgw.service.impl;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.geedgenetworks.utils.StringUtil;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mesalab.cn.enums.RangeTypeEnum;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.OutputMode;
import com.mesalab.common.enums.HttpStatusCodeEnum;
import com.mesalab.common.enums.QueryOption;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.TreeUtils;
import com.mesalab.knowledge.entity.*;
import com.mesalab.knowledge.enums.MatchEnum;
import com.mesalab.knowledge.enums.SortEnum;
import com.mesalab.knowledge.service.KnowledgeService;
import com.mesalab.qgw.constant.dsl.LiveChartConstants;
import com.mesalab.qgw.model.dsl.LiveChartProtocol;
import com.mesalab.qgw.constant.DslIdentifierNameConst;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.qgw.model.basic.DSLQueryContext;
import com.mesalab.qgw.model.basic.SQLQueryContext;
import com.mesalab.qgw.service.DSLService;
import com.mesalab.qgw.service.DatabaseService;
import com.mesalab.qgw.service.SQLSyncQueryService;
import com.mesalab.services.common.property.SqlPropertySourceFactory;
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
public class DslServiceImpl implements DSLService {

    private static final Log log = LogFactory.get();

    @Autowired
    Environment environment;
    @Autowired
    DatabaseService databaseService;
    @Autowired
    SQLSyncQueryService sqlSyncQueryService;
    @Autowired
    KnowledgeService knowledgeService;

    private static final String TABLE_TRAFFIC_GENERAL_STAT = "traffic_general_stat";
    private static final String TABLE_APPLICATION_PROTOCOL_STAT = "application_protocol_stat";
    private static final String TABLE_SESSION_RECORD = "session_record";

    private static final String LOGICAL_TYPE_TIMESTAMP = "timestamp";
    private static final String LOGICAL_TYPE_UNIX_TIMESTAMP = "unix_timestamp";

    @Override
    public BaseResult execDsl(@RequestBody DSLQueryContext dslProfile, boolean isDryRun) {
        log.info("HTTP REST DSL, params is: {}", dslProfile);
        if (DslIdentifierNameConst.APPLICATION_AND_PROTOCOL_SUMMARY.equals(dslProfile.getName())) {
            return appAndProtocolSummary(dslProfile, isDryRun);
        } else if (DslIdentifierNameConst.APPLICATION_AND_PROTOCOL_TREE_COMPOSITION.equals(dslProfile.getName())) {
            return appAndProtocolTreeComposition(dslProfile, isDryRun);
        } else if (DslIdentifierNameConst.APPLICATION_AND_PROTOCOL_TREE_THROUGHPUT.equals(dslProfile.getName())) {
            return applicationAndProtocolTreeThroughput(dslProfile, isDryRun);
        } else if (DslIdentifierNameConst.APPLICATION_AND_PROTOCOL_TOP_APPS.equals(dslProfile.getName())) {
            return applicationAndProtocolTopApp(dslProfile, isDryRun);
        } else if (DslIdentifierNameConst.APPLICATION_AND_PROTOCOL_APP_RELATED_INTERNAL_IPS.equals(dslProfile.getName())) {
            return applicationAndProtocolAppRelatedInternalIps(dslProfile, isDryRun);
        } else if (DslIdentifierNameConst.APPLICATION_AND_PROTOCOL_APP_THROUGHPUT.equals(dslProfile.getName())) {
            return applicationAndProtocolAppThroughput(dslProfile, isDryRun);
        } else if (DslIdentifierNameConst.APPLICATION_AND_PROTOCOL_APP_SUMMARY.equals(dslProfile.getName())) {
            return applicationAndProtocolAppSummary(dslProfile, isDryRun);
        } else if (DslIdentifierNameConst.IP_LEARNING_FQDN_RELATE_IP.equals(dslProfile.getName())) {
            return ipLearningFqdnRelateIp(dslProfile, isDryRun);
        } else if (DslIdentifierNameConst.IP_LEARNING_ACTIVE_IP.equals(dslProfile.getName())) {
            return ipLearningActiveIp(dslProfile, isDryRun);
        }
        throw new BusinessException(HttpStatusCodeEnum.NOT_FOUND.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(), String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(), dslProfile.getName()));
    }

    @Override
    public BaseResult appAndProtocolSummary(DSLQueryContext dslProfile, boolean isDryRun) {
        String queryOption = isDryRun ? QueryOption.SYNTAX_VALIDATION.getValue() : QueryOption.REAL_TIME.getValue();
        BaseResult asymmetricFlowsResult = sqlSyncQueryService.executeQuery(
                SQLQueryContext.builder().originalSQL(dslProfile.toSql(environment.getProperty("APPLICATION_AND_PROTOCOL_SUMMARY_ASYMMETRIC_FLOWS_STAT"), TABLE_TRAFFIC_GENERAL_STAT, databaseService.getPartitionKey(TABLE_TRAFFIC_GENERAL_STAT), LOGICAL_TYPE_TIMESTAMP)).option(queryOption).build());
        if (!asymmetricFlowsResult.isSuccess()) {
            throw new BusinessException(asymmetricFlowsResult.getStatus(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(), String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(), asymmetricFlowsResult.getMessage()));
        }

        BaseResult networkTrafficStatResult = sqlSyncQueryService.executeQuery(
                SQLQueryContext.builder().originalSQL(dslProfile.toSql(environment.getProperty("APPLICATION_AND_PROTOCOL_SUMMARY_TRAFFIC_STAT"), TABLE_APPLICATION_PROTOCOL_STAT, databaseService.getPartitionKey(TABLE_APPLICATION_PROTOCOL_STAT), LOGICAL_TYPE_TIMESTAMP)).option(queryOption).build());
        if (!networkTrafficStatResult.isSuccess()) {
            throw new BusinessException(networkTrafficStatResult.getStatus(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(), String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(), networkTrafficStatResult.getMessage()));
        }

        BaseResult networkTcpStatResult = sqlSyncQueryService.executeQuery(
                SQLQueryContext.builder().originalSQL(dslProfile.toSql(environment.getProperty("APPLICATION_AND_PROTOCOL_SUMMARY_TCP_STAT"), TABLE_APPLICATION_PROTOCOL_STAT, databaseService.getPartitionKey(TABLE_APPLICATION_PROTOCOL_STAT), LOGICAL_TYPE_TIMESTAMP)).option(queryOption).build());
        if (!networkTcpStatResult.isSuccess()) {
            throw new BusinessException(networkTcpStatResult.getStatus(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(), String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(), networkTcpStatResult.getMessage()));
        }

        BaseResult networkAppStatResult = sqlSyncQueryService.executeQuery(
                SQLQueryContext.builder().originalSQL(dslProfile.toSql(environment.getProperty("APPLICATION_AND_PROTOCOL_SUMMARY_APP_STAT"), TABLE_APPLICATION_PROTOCOL_STAT, databaseService.getPartitionKey(TABLE_APPLICATION_PROTOCOL_STAT), LOGICAL_TYPE_TIMESTAMP)).option(queryOption).build());
        if (!networkAppStatResult.isSuccess()) {
            throw new BusinessException(networkAppStatResult.getStatus(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(), String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(), networkAppStatResult.getMessage()));
        }

        List<Map> asymmetricFlowsData = (List<Map>) asymmetricFlowsResult.getData();
        List<Map> trafficStatData = (List<Map>) networkTrafficStatResult.getData();
        List<Map> tcpData = (List<Map>) networkTcpStatResult.getData();
        List<Map> appData = (List<Map>) networkAppStatResult.getData();

        Map<String, Object> data = Maps.newHashMap();

        if (!asymmetricFlowsData.isEmpty()) {
            data.putAll(asymmetricFlowsData.get(0));
        }
        if (!trafficStatData.isEmpty()) {
            data.putAll(trafficStatData.get(0));
        }
        if (!tcpData.isEmpty()) {
            data.putAll(tcpData.get(0));
        }
        if (!appData.isEmpty()) {
            data.putAll(appData.get(0));
        }

        long asymmetricFlows = Long.parseLong(String.valueOf(
                data.get(LiveChartConstants.NETWORK_OVERVIEW_METRIC_ASYMMETRIC_FLOWS_BYTES) == null ? 0 : data.get(LiveChartConstants.NETWORK_OVERVIEW_METRIC_ASYMMETRIC_FLOWS_BYTES)));
        long totalSessionUseOnAsymmetricFlows = Long.parseLong(String.valueOf(
                data.get(LiveChartConstants.NETWORK_OVERVIEW_METRIC_TOTAL_BYTES_USE_ON_ASYMMETRIC_FLOWS) == null ? 0 : data.get(LiveChartConstants.NETWORK_OVERVIEW_METRIC_TOTAL_BYTES_USE_ON_ASYMMETRIC_FLOWS)));

        long totalSessions = Long.parseLong(String.valueOf(
                data.get(LiveChartConstants.NETWORK_OVERVIEW_METRIC_TOTAL_SESSIONS) == null ? 0 : data.get(LiveChartConstants.NETWORK_OVERVIEW_METRIC_TOTAL_SESSIONS)));
        long totalBytes = Long.parseLong(String.valueOf(
                data.get(LiveChartConstants.NETWORK_OVERVIEW_METRIC_TOTAL_BYTES) == null ? 0 : data.get(LiveChartConstants.NETWORK_OVERVIEW_METRIC_TOTAL_BYTES)));
        long totalPackets = Long.parseLong(String.valueOf(
                data.get(LiveChartConstants.NETWORK_OVERVIEW_METRIC_TOTAL_PACKETS) == null ? 0 : data.get(LiveChartConstants.NETWORK_OVERVIEW_METRIC_TOTAL_PACKETS)));

        long fragmentationPackets = Long.parseLong(String.valueOf(
                data.get(LiveChartConstants.NETWORK_OVERVIEW_METRIC_FRAGMENTATION_PACKETS) == null ? 0 : data.get(LiveChartConstants.NETWORK_OVERVIEW_METRIC_FRAGMENTATION_PACKETS)));
        long dataRate = Long.parseLong(String.valueOf(
                data.get(LiveChartConstants.NETWORK_OVERVIEW_METRIC_DATA_RATE) == null ? 0 : data.get(LiveChartConstants.NETWORK_OVERVIEW_METRIC_DATA_RATE)));

        long tcpRetransmissionsPackets = Long.parseLong(String.valueOf(
                data.get(LiveChartConstants.NETWORK_OVERVIEW_METRIC_TCP_RETRANSMISSIONS_PACKETS) == null ? 0 : data.get(LiveChartConstants.NETWORK_OVERVIEW_METRIC_TCP_RETRANSMISSIONS_PACKETS)));
        long tcpTotalPackets = Long.parseLong(String.valueOf(
                data.get(LiveChartConstants.NETWORK_OVERVIEW_METRIC_TCP_TOTAL_PACKETS) == null ? 0 : data.get(LiveChartConstants.NETWORK_OVERVIEW_METRIC_TCP_TOTAL_PACKETS)));
        long unknownAppBytes = Long.parseLong(String.valueOf(
                data.get(LiveChartConstants.NETWORK_OVERVIEW_METRIC_UNKNOWN_APP_BYTES) == null ? 0 : data.get(LiveChartConstants.NETWORK_OVERVIEW_METRIC_UNKNOWN_APP_BYTES)));

        double unknownAppPercent = totalBytes == 0 ? 0.0
                : NumberUtil.div(BigDecimal.valueOf(unknownAppBytes), BigDecimal.valueOf(totalBytes), 4).doubleValue();
        double asymmetricFlowsPercent = totalSessionUseOnAsymmetricFlows == 0 ? 0.0
                : NumberUtil.div(BigDecimal.valueOf(asymmetricFlows), BigDecimal.valueOf(totalSessionUseOnAsymmetricFlows), 4).doubleValue();
        double tcpRetransmissionsPercent = tcpTotalPackets == 0 ? 0.0
                : NumberUtil.div(BigDecimal.valueOf(tcpRetransmissionsPackets), BigDecimal.valueOf(tcpTotalPackets), 4).doubleValue();
        double fragmentationPacketsPercent = totalPackets == 0 ? 0.0
                : NumberUtil.div(BigDecimal.valueOf(fragmentationPackets), BigDecimal.valueOf(totalPackets), 4).doubleValue();

        List result = new ArrayList<>();
        Map resultMap = new LinkedHashMap<>();
        resultMap.put(LiveChartConstants.NETWORK_OVERVIEW_METRIC_TOTAL_SESSIONS, totalSessions);
        resultMap.put(LiveChartConstants.NETWORK_OVERVIEW_METRIC_DATA_RATE, dataRate);
        resultMap.put(LiveChartConstants.NETWORK_OVERVIEW_METRIC_TOTAL_BYTES, totalBytes);
        resultMap.put(LiveChartConstants.NETWORK_OVERVIEW_METRIC_TOTAL_PACKETS, totalPackets);
        resultMap.put(LiveChartConstants.NETWORK_OVERVIEW_METRIC_UNKNOWN_APP_BYTES, unknownAppBytes);
        resultMap.put(LiveChartConstants.NETWORK_OVERVIEW_METRIC_UNKNOWN_APP_PERCENT, unknownAppPercent);
        resultMap.put(LiveChartConstants.NETWORK_OVERVIEW_METRIC_ASYMMETRIC_FLOWS_BYTES, asymmetricFlows);
        resultMap.put(LiveChartConstants.NETWORK_OVERVIEW_METRIC_ASYMMETRIC_FLOWS_PERCENT, asymmetricFlowsPercent);
        resultMap.put(LiveChartConstants.NETWORK_OVERVIEW_METRIC_TCP_RETRANSMISSIONS_PACKETS, tcpRetransmissionsPackets);
        resultMap.put(LiveChartConstants.NETWORK_OVERVIEW_METRIC_TCP_RETRANSMISSIONS_PERCENT, tcpRetransmissionsPercent);
        resultMap.put(LiveChartConstants.NETWORK_OVERVIEW_METRIC_FRAGMENTATION_PACKETS, fragmentationPackets);
        resultMap.put(LiveChartConstants.NETWORK_OVERVIEW_METRIC_FRAGMENTATION_PERCENT, fragmentationPacketsPercent);
        result.add(resultMap);

        Map statistics = asymmetricFlowsResult.getStatistics();
        Map networkTrafficStatistics = networkTrafficStatResult.getStatistics();
        Map networkTcpStatistics = networkTcpStatResult.getStatistics();
        Map networkAppStatistics = networkAppStatResult.getStatistics();
        for (Object key : statistics.keySet()) {
            if ("result_rows".equals(key.toString())) {
                continue;
            }
            statistics.put(key, Long.parseLong(networkTrafficStatistics.get(key).toString()) + Long.parseLong(networkTcpStatistics.get(key).toString()) + Long.parseLong(networkAppStatistics.get(key).toString()));
        }

        return BaseResultGenerator.success("ok", result, statistics);
    }

    @Override
    public BaseResult appAndProtocolTreeComposition(DSLQueryContext dslProfile, boolean isDryRun) {
        String sql = dslProfile.toSql(environment.getProperty("APPLICATION_AND_PROTOCOL_TREE_COMPOSITION"), TABLE_APPLICATION_PROTOCOL_STAT, databaseService.getPartitionKey(TABLE_APPLICATION_PROTOCOL_STAT), LOGICAL_TYPE_TIMESTAMP);
        BaseResult baseResult = sqlSyncQueryService.executeQuery(queryBuild(sql, isDryRun));
        if (!baseResult.isSuccess()) {
            throw new BusinessException(baseResult.getStatus(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(), String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(), baseResult.getMessage()));
        }
        List<Map> resultList = (List<Map>) baseResult.getData();
        if (StringUtil.isEmpty(resultList)) {
            return BaseResultGenerator.success("ok", resultList, baseResult.getStatistics());
        }
        List<LiveChartProtocol> protocols = buildHierarchicalStructure(buildFlatStructure(resultList));
        return BaseResultGenerator.success("ok", protocols, baseResult.getStatistics());
    }

    @Override
    public BaseResult applicationAndProtocolTreeThroughput(DSLQueryContext dslProfile, boolean isDryRun) {
        String sql = dslProfile.toSql(environment.getProperty("APPLICATION_AND_PROTOCOL_TREE_THROUGHPUT"), TABLE_APPLICATION_PROTOCOL_STAT, databaseService.getPartitionKey(TABLE_APPLICATION_PROTOCOL_STAT), LOGICAL_TYPE_TIMESTAMP);
        BaseResult baseResult = sqlSyncQueryService.executeQuery(queryBuild(sql, isDryRun));
        if (!baseResult.isSuccess()) {
            throw new BusinessException(baseResult.getStatus(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(), String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(), baseResult.getMessage()));
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
    public BaseResult applicationAndProtocolTopApp(DSLQueryContext dslProfile, boolean isDryRun) {
        if (StrUtil.isEmpty(dslProfile.getGranularity())) {
            dslProfile.setGranularity("PT15S");
        }
        String sql = dslProfile.toSql(environment.getProperty("APPLICATION_AND_PROTOCOL_TOP_APPS"), TABLE_APPLICATION_PROTOCOL_STAT, databaseService.getPartitionKey(TABLE_APPLICATION_PROTOCOL_STAT), LOGICAL_TYPE_TIMESTAMP);
        BaseResult baseResult = sqlSyncQueryService.executeQuery(queryBuild(sql, isDryRun));
        if (!baseResult.isSuccess()) {
            throw new BusinessException(baseResult.getStatus(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(), String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(), baseResult.getMessage()));
        }
        return baseResult;
    }

    @Override
    public BaseResult applicationAndProtocolAppRelatedInternalIps(DSLQueryContext dslProfile, boolean isDryRun) {
        String sql = dslProfile.toSql(environment.getProperty("APPLICATION_AND_PROTOCOL_APP_RELATED_INTERNAL_IPS"), TABLE_SESSION_RECORD, databaseService.getPartitionKey(TABLE_SESSION_RECORD), LOGICAL_TYPE_UNIX_TIMESTAMP);
        BaseResult baseResult = sqlSyncQueryService.executeQuery(queryBuild(sql, isDryRun));
        if (!baseResult.isSuccess()) {
            throw new BusinessException(baseResult.getStatus(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(), String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(), baseResult.getMessage()));
        }
        return baseResult;
    }

    @Override
    public BaseResult applicationAndProtocolAppThroughput(DSLQueryContext dslProfile, boolean isDryRun) {
        String sql = dslProfile.toSql(environment.getProperty("APPLICATION_AND_PROTOCOL_APP_THROUGHPUT"), TABLE_APPLICATION_PROTOCOL_STAT, databaseService.getPartitionKey(TABLE_APPLICATION_PROTOCOL_STAT), LOGICAL_TYPE_TIMESTAMP);
        BaseResult baseResult = sqlSyncQueryService.executeQuery(queryBuild(sql, isDryRun));
        if (!baseResult.isSuccess()) {
            throw new BusinessException(baseResult.getStatus(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(), String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(), baseResult.getMessage()));
        }
        return baseResult;
    }

    @Override
    public BaseResult applicationAndProtocolAppSummary(DSLQueryContext dslProfile, boolean isDryRun) {
        String sql = dslProfile.toSql(environment.getProperty("APPLICATION_AND_PROTOCOL_APP_SUMMARY"), TABLE_APPLICATION_PROTOCOL_STAT, databaseService.getPartitionKey(TABLE_APPLICATION_PROTOCOL_STAT), LOGICAL_TYPE_TIMESTAMP);
        BaseResult baseResult = sqlSyncQueryService.executeQuery(queryBuild(sql, isDryRun));
        if (!baseResult.isSuccess()) {
            throw new BusinessException(baseResult.getStatus(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(), String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(), baseResult.getMessage()));
        }
        return baseResult;
    }

    @Override
    public BaseResult ipLearningFqdnRelateIp(DSLQueryContext dslProfile, boolean isDryRun) {
        String queryType = "iplearning";
        String dataSource = "IP_LEARNING_VIEW";
        DSLObject dslObject = getDslObject(dslProfile, queryType, dataSource);
        log.info("DSLProfile parse to DSLObject: {}", JSON.toJSONString(dslObject));
        return knowledgeService.query(dslObject);
    }

    @Override
    public BaseResult ipLearningActiveIp(DSLQueryContext dslProfile, boolean isDryRun) {
        String queryType = "ippool";
        String dataSource = "IP_VIEW";
        DSLObject dslObject = getDslObject(dslProfile, queryType, dataSource);
        log.info("DSLProfile parse to DSLObject: {}", JSON.toJSONString(dslObject));
        return knowledgeService.query(dslObject);
    }

    private static DSLObject getDslObject(DSLQueryContext dslProfile, String queryType, String dataSource) {
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
            throw new BusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(), String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(), e.getMessage()));
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
            throw new BusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(), String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(), e.getMessage()));
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
                ItemsList rightItemsList = expr.getRightItemsList();
                if (rightItemsList instanceof ExpressionList && ((ExpressionList) rightItemsList).getExpressions().get(0) instanceof LongValue) {
                    intParseIn(expr);
                } else {
                    strParseIn(expr);
                }
            }

            private void intParseIn(InExpression expr) {
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

            private void strParseIn(InExpression expr) {
                String left = String.valueOf(expr.getLeftExpression());
                if (matches.stream().anyMatch(o -> o.getFieldKey().equals(left) && o.getType().equals(MatchEnum.EXACTLY.getType()))) {
                    for (Match match : matches) {
                        if (match.getFieldKey().equals(left) && match.getType().equals(MatchEnum.EXACTLY.getType())) {
                            ItemsList rightItemsList = expr.getRightItemsList();
                            rightItemsList.accept(new ItemsListVisitorAdapter() {
                                @Override
                                public void visit(ExpressionList expressionList) {
                                    for (Expression expression : expressionList.getExpressions()) {
                                        if (expression instanceof StringValue) {
                                            match.getFieldValues().add(((StringValue) expression).getValue());
                                        } else if (expression instanceof LongValue) {
                                            match.getFieldValues().add(((LongValue) expression).getValue());
                                        }

                                    }
                                }

                            });
                        }
                    }
                } else {
                    Match match = new Match();
                    match.setFieldKey(left);
                    match.setFieldValues(Lists.newArrayList());
                    match.setType(MatchEnum.EXACTLY.getType());
                    ItemsList rightItemsList = expr.getRightItemsList();
                    rightItemsList.accept(new ItemsListVisitorAdapter() {
                        @Override
                        public void visit(ExpressionList expressionList) {
                            for (Expression expression : expressionList.getExpressions()) {
                                if (expression instanceof StringValue) {
                                    match.getFieldValues().add(((StringValue) expression).getValue());
                                } else if (expression instanceof LongValue) {
                                    match.getFieldValues().add(((LongValue) expression).getValue());
                                }

                            }
                        }
                    });
                    matches.add(match);
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

    private SQLQueryContext queryBuild(String sql, boolean isDryRun) {
        String queryOption = isDryRun ? QueryOption.SYNTAX_VALIDATION.getValue() : QueryOption.REAL_TIME.getValue();
        return SQLQueryContext.builder().format(OutputMode.JSON.getValue()).originalSQL(sql).option(queryOption).build();
    }

    @Override
    public List<LiveChartProtocol> buildFlatStructure(List<Map> protocols) {
        List<LiveChartProtocol> nodes = Lists.newArrayList();
        for (Map protocolMap : protocols) {
            LiveChartProtocol protocol = convertStringToObject(JSON.toJSONString(protocolMap, JSONWriter.Feature.LargeObject));
            if (StringUtil.isNotEmpty(protocol)) {
                nodes.add(protocol);
            }

        }
        LiveChartProtocol root = new LiveChartProtocol(LiveChartConstants.PROTOCOL_TREE_ROOT_NAME, LiveChartConstants.PROTOCOL_TREE_ROOT_NAME, null);
        Map<String, Long> protocolsAndApplications = nodes.stream().collect(Collectors.groupingBy(LiveChartProtocol::getName, Collectors.counting()));
        root.addMetric(LiveChartConstants.PROTOCOL_TREE_METRIC_ENCAPSULATION_PATHS, nodes.size());
        root.addMetric(LiveChartConstants.PROTOCOL_TREE_METRIC_PROTOCOLS_AND_APPLICATIONS, protocolsAndApplications.size());
        List<LiveChartProtocol> roots = nodes.stream().filter(o -> StringUtil.isBlank(o.getParentId())).collect(Collectors.toList());
        roots.forEach(item -> {
            root.setSentBytes(root.getSentBytes() + item.getSentBytes());
            root.setReceivedBytes(root.getReceivedBytes() + item.getReceivedBytes());
            root.setTotalSentBytes(root.getTotalSentBytes() + item.getSentBytes());
            root.setTotalReceivedBytes(root.getTotalReceivedBytes() + item.getReceivedBytes());
        });
        nodes.forEach(item -> {
            item.setId(LiveChartConstants.PROTOCOL_TREE_ROOT_NAME + LiveChartConstants.ENCAPSULATION_PATH_SEPARATOR + item.getId());
        });
        nodes.add(root);
        return nodes;
    }

    @Override
    public List<LiveChartProtocol> buildHierarchicalStructure(List<LiveChartProtocol> nodes) {

        List<LiveChartProtocol> targetNodes = groupByName(nodes);

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
        Map<String, List<LiveChartProtocol>> protocolMap = Maps.newLinkedHashMap();
        List<LiveChartProtocol> roots = targetNodes.stream().filter(o -> StringUtil.isBlank(o.getParentId())).collect(Collectors.toList());
        protocolMap.put(LiveChartConstants.PROTOCOLS_PARENT_ID, roots);
        targetNodes.stream().filter(o -> StringUtil.isNotBlank(o.getParentId())).forEach(x -> {
            if (StringUtil.isEmpty(protocolMap.get(x.getParentId()))) {
                protocolMap.put(x.getParentId(), Lists.newArrayList(x));
            } else {
                protocolMap.get(x.getParentId()).add(x);
            }
        });

        List<LiveChartProtocol> protocols = TreeUtils.mergeTree(protocolMap, LiveChartConstants.PROTOCOLS_PARENT_ID, LiveChartProtocol::getId, LiveChartProtocol::setChildrens);
        for (LiveChartProtocol protocolTree : protocols) {
            List<LiveChartProtocol> ethernetList = protocolTree.getChildrens().stream().filter(
                    p -> LiveChartConstants.PROTOCOL_ETHERNET_NODE.equals(p.getName())).collect(Collectors.toList());
            if (protocolTree.getChildrens().size() != ethernetList.size()) {
                protocolTree.setChildrens(ethernetList);
                protocolTree.setReceivedBytes(ethernetList.isEmpty() || StringUtil.isEmpty(ethernetList.get(0)) ? 0 : ethernetList.get(0).getReceivedBytes());
                protocolTree.setSentBytes(ethernetList.isEmpty() || StringUtil.isEmpty(ethernetList.get(0)) ? 0 : ethernetList.get(0).getSentBytes());
                log.warn("Protocol Tree Exceptional data occurs and non-{} protocols are filtered out: {}", LiveChartConstants.PROTOCOL_ETHERNET_NODE, protocolTree.getChildrens());
            }
        }
        return protocols;
    }

    /**
     * 基于Name聚合，统计Protocol和Application在全栈下的流量
     *
     * @param nodes
     * @return
     */
    private List<LiveChartProtocol> groupByName(List<LiveChartProtocol> nodes) {
        Map<String, List<LiveChartProtocol>> resultMap = nodes.stream()
                .collect(Collectors.groupingBy(LiveChartProtocol::getName, Collectors.collectingAndThen(Collectors.toList(), protocols -> {

                    List<LiveChartProtocol> parentNodes = protocols.stream()
                            .filter(p -> StringUtil.isBlank(p.getParentId()) || !Splitter.on(".").omitEmptyStrings().splitToList(p.getParentId()).contains(p.getName()))
                            .collect(Collectors.toList());
                    Map<String, Long> summedMetrics = Maps.newLinkedHashMap();
                    Map<String, Long> totalBytesMetrics = Maps.newLinkedHashMap();
                    totalBytesMetrics.put(LiveChartConstants.PROTOCOL_TREE_METRIC_TOTAL_SENT_BYTES, 0L);
                    totalBytesMetrics.put(LiveChartConstants.PROTOCOL_TREE_METRIC_TOTAL_RECEIVED_BYTES, 0L);

                    parentNodes.stream().filter(p -> !p.getName().equalsIgnoreCase(LiveChartConstants.PROTOCOL_TREE_ROOT_NAME)).forEach(protocolTree -> {
                        totalBytesMetrics.put(LiveChartConstants.PROTOCOL_TREE_METRIC_TOTAL_SENT_BYTES, totalBytesMetrics.get(LiveChartConstants.PROTOCOL_TREE_METRIC_TOTAL_SENT_BYTES) + protocolTree.getSentBytes());
                        totalBytesMetrics.put(LiveChartConstants.PROTOCOL_TREE_METRIC_TOTAL_RECEIVED_BYTES, totalBytesMetrics.get(LiveChartConstants.PROTOCOL_TREE_METRIC_TOTAL_RECEIVED_BYTES) + protocolTree.getReceivedBytes());
                        for (String key : protocolTree.getMetrics().keySet()) {
                            String totalKey = StrUtil.addPrefixIfNot(key, "total_");
                            if (StringUtil.isEmpty(summedMetrics.get(totalKey))) {
                                summedMetrics.put(totalKey, Long.valueOf(protocolTree.getMetrics().get(key).toString()));
                            } else {
                                summedMetrics.put(totalKey, summedMetrics.get(totalKey)
                                        + Long.parseLong(protocolTree.getMetrics().get(key).toString()));
                            }
                        }
                    });

                    protocols.stream().filter(p -> !p.getName().equalsIgnoreCase(LiveChartConstants.PROTOCOL_TREE_ROOT_NAME)).forEach(protocolTree -> {
                        protocolTree.setTotalSentBytes(totalBytesMetrics.get(LiveChartConstants.PROTOCOL_TREE_METRIC_TOTAL_SENT_BYTES));
                        protocolTree.setTotalReceivedBytes(totalBytesMetrics.get(LiveChartConstants.PROTOCOL_TREE_METRIC_TOTAL_RECEIVED_BYTES));
                        protocolTree.getMetrics().putAll(summedMetrics);
                    });

                    return protocols;
                })));

        List<LiveChartProtocol> protocols = Lists.newArrayList();
        resultMap.values().forEach(o -> {
            protocols.addAll(o);
        });

        return protocols;
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

    private LiveChartProtocol convertStringToObject(String protocolString) {
        LiveChartProtocol protocol = null;
        try {
            Map<String, Object> results = JSON.parseObject(protocolString, Map.class);
            String protocolId = String.valueOf(results.get("protocol_stack_id"));
            if (StringUtil.isBlank(protocolId)) {
                log.warn("Protocol Stack ID is NULL {}", protocolString);
                return null;
            }
            long sessions = Long.parseLong(Optional.ofNullable(
                    results.get(LiveChartConstants.PROTOCOL_TREE_METRIC_SESSIONS)).orElse(0).toString());
            long sentBytes = Long.parseLong(Optional.ofNullable(
                    results.get(LiveChartConstants.PROTOCOL_TREE_METRIC_C2S_BYTES)).orElse(0).toString());
            long receivedBytes = Long.parseLong(Optional.ofNullable(
                    results.get(LiveChartConstants.PROTOCOL_TREE_METRIC_S2C_BYTES)).orElse(0).toString());
            long sentPackets = Long.parseLong(Optional.ofNullable(
                    results.get(LiveChartConstants.PROTOCOL_TREE_METRIC_C2S_PKTS)).orElse(0).toString());
            long receivedPackets = Long.parseLong(Optional.ofNullable(
                    results.get(LiveChartConstants.PROTOCOL_TREE_METRIC_S2C_PKTS)).orElse(0).toString());
            List<String> protocols = Splitter.on(LiveChartConstants.ENCAPSULATION_PATH_SEPARATOR).omitEmptyStrings().splitToList(protocolId);
            String protocolName = StringUtil.isNotEmpty(protocols) ? protocols.get(protocols.size() - 1) : null;
            protocol = new LiveChartProtocol(protocolId, protocolName, null);
            protocol.setSentBytes(sentBytes);
            protocol.setReceivedBytes(receivedBytes);
            protocol.getMetrics().put(LiveChartConstants.PROTOCOL_TREE_METRIC_SENT_PACKETS, sentPackets);
            protocol.getMetrics().put(LiveChartConstants.PROTOCOL_TREE_METRIC_RECEIVED_PACKETS, receivedPackets);
            protocol.getMetrics().put(LiveChartConstants.PROTOCOL_TREE_METRIC_SESSIONS, sessions);
        } catch (BusinessException e) {
            log.error("Convert Json String to  Protocol Tree Object Error ", e);
        }

        return protocol;
    }

}
