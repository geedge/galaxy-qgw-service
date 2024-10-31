package com.mesalab.qgw.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayway.jsonpath.JsonPath;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.OutputMode;
import com.mesalab.qgw.model.basic.DSLQueryContext;
import com.mesalab.qgw.model.basic.DSLQueryRequestParam;
import com.mesalab.qgw.model.basic.QueryCache;
import com.mesalab.qgw.model.basic.SQLQueryContext;
import com.mesalab.qgw.service.DatabaseService;
import com.mesalab.qgw.service.LogQueryService;
import com.mesalab.qgw.service.SQLSyncQueryService;
import org.joda.time.Period;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * TODO
 *
 * @Classname LogQueryServiceImpl
 * @Date 2024/9/18 11:41
 * @Author wWei
 */
@Service("logQueryService")
public class LogQueryServiceImpl implements LogQueryService {
    private static final String LOG_DEFAULT_LOGICAL_TYPE = "unix_timestamp";
    private DatabaseService databaseService;
    private Environment environment;
    private SQLSyncQueryService sqlSyncQueryService;

    private static final Map<String, String> TIMELINE_SOURCE_TYPE_MAP = new HashMap<>();

    static {
        TIMELINE_SOURCE_TYPE_MAP.put("session_record", ", decoded_as AS type");
        TIMELINE_SOURCE_TYPE_MAP.put("voip_record", ", decoded_as AS type");
        TIMELINE_SOURCE_TYPE_MAP.put("security_event", ", security_action AS type");
        TIMELINE_SOURCE_TYPE_MAP.put("proxy_event", ", proxy_action AS type");
    }

    @Override
    public BaseResult run(DSLQueryRequestParam param) {
        String partitionKey = databaseService.getPartitionKey(param.getDataSource());
        LinkedHashMap<String, Object> schemaByName = databaseService.getSchemaByName(param.getDataSource());
        List<String> partitionKeyLogicalTypes = JsonPath.read(schemaByName, "$.fields[?(@.name == \"" + partitionKey + "\")].type.logicalType");
        String partitionKeyLogicalType = partitionKeyLogicalTypes.isEmpty() ? LOG_DEFAULT_LOGICAL_TYPE : partitionKeyLogicalTypes.get(0);
        DSLQueryContext dslQueryContext = BeanUtil.copyProperties(param, DSLQueryContext.class);
        String sqlTemplate = dslQueryContext.toSql(
                environment.getProperty("LOG_QUERY_TIMELINE")
                , param.getDataSource()
                , databaseService.getPartitionKey(param.getDataSource())
                , partitionKeyLogicalType);
        String timelineType = TIMELINE_SOURCE_TYPE_MAP.getOrDefault(param.getDataSource(), "");
        String sql = String.format(sqlTemplate, partitionKey, timelineType, timelineType.isEmpty() ? "" : ", type");
        return sqlSyncQueryService.executeQuery(SQLQueryContext.builder().originalSQL(sql).build());
    }

    @Override
    public BaseResult getQueryListByQueryContext(QueryCache queryCache, String fields, int limit, int offset) {
        BaseResult<Object> baseResult = queryCache.getBaseResult();
        Object dataObj = baseResult.getData();
        if (dataObj == null || ((List) dataObj).isEmpty()) {
            return BaseResultGenerator.success(null, null, OutputMode.JSON.getValue(), null, Lists.newArrayList());
        }
        List<Map<String, Object>> dataList = (List<Map<String, Object>>) dataObj;
        dataList = dataList.parallelStream()
                .sorted((map1, map2) -> {
                    DateTime time1 = DateUtil.parse(String.valueOf(map1.get("stat_time")));
                    DateTime time2 = DateUtil.parse(String.valueOf(map2.get("stat_time")));
                    return time2.compareTo(time1); // 降序排序
                })
                .collect(Collectors.toList());
        AtomicLong totalCount = new AtomicLong();
        DateTime cursorStartTime = null;
        DateTime cursorEndTime = null;
        for (Map<String, Object> item : dataList) {
            long itemCount = Long.parseLong(String.valueOf(item.getOrDefault("count", 0)));
            DateTime itemTime = DateUtil.parse(String.valueOf(item.get("stat_time")));
            if (itemCount > 0 && totalCount.get() == 0) {
                cursorEndTime = itemTime;
            }
            totalCount.addAndGet(itemCount);
            cursorStartTime = itemTime;
            if (totalCount.get() >= (limit + offset)) {
                break;
            }
        }
        DSLQueryRequestParam requestParam = BeanUtil.copyProperties(queryCache.getRequestParam(), DSLQueryRequestParam.class);
        String granularity = requestParam.getGranularity();
        DSLQueryContext dslQueryContextTemp = BeanUtil.copyProperties(requestParam, DSLQueryContext.class);
        List<String> intervals = dslQueryContextTemp.getIntervals();
        String[] split = intervals.get(0).split("/");
        DateTime start = DateUtil.parse(split[0]);
        DateTime end = DateUtil.parse(split[1]);

        int seconds = Period.parse(granularity).toStandardSeconds().getSeconds();
        DateTime dateTime = DateUtil.offsetSecond(cursorEndTime, seconds);
        if (DateUtil.compare(end, dateTime) > 0) {
            cursorEndTime = dateTime;
        } else {
            cursorEndTime = end;
        }

        if (DateUtil.compare(start, cursorStartTime) > 0) {
            cursorStartTime = start;
        }
        String startFormat = DateUtil.format(cursorStartTime, DatePattern.UTC_PATTERN);
        String endFormat = DateUtil.format(cursorEndTime, DatePattern.UTC_PATTERN);
        String dataSource = requestParam.getDataSource();
        String partitionKey = databaseService.getPartitionKey(requestParam.getDataSource());
        List<String> list = Lists.newArrayList(startFormat + "/" + endFormat);
        dslQueryContextTemp.setIntervals(list);
        dslQueryContextTemp.setLimit(offset + "," + limit);

        String sqlTemplate = environment.getProperty("LOG_QUERY_LIST");
        LinkedHashMap<String, Object> schemaByName = databaseService.getSchemaByName(requestParam.getDataSource());
        List<String> partitionKeyLogicalTypes = JsonPath.read(schemaByName, "$.fields[?(@.name == \"" + partitionKey + "\")].type.logicalType");
        String partitionKeyLogicalType = partitionKeyLogicalTypes.isEmpty() ? null : partitionKeyLogicalTypes.get(0);

        String sqlTemplate2 = dslQueryContextTemp.toSql(sqlTemplate, dataSource, partitionKey, partitionKeyLogicalType);
        String sql = String.format(sqlTemplate2, StrUtil.isEmpty(fields) ? String.join(", ", requestParam.getFields()) : fields, partitionKey + " DESC ");
        return sqlSyncQueryService.executeQuery(SQLQueryContext.builder().originalSQL(sql).build());
    }

    @Override
    public BaseResult getQueryCountByQueryContext(QueryCache queryCache) {
        BaseResult<Object> baseResult = queryCache.getBaseResult();
        Object data = baseResult.getData();

        long totalCount = (data == null || ((List<?>) data).isEmpty()) ? 0 :
                ((List<Map<String, Object>>) data).stream()
                        .mapToLong(map -> Long.parseLong(String.valueOf(map.getOrDefault("count", 0))))
                        .sum();
        List<Map<String, Object>> result = Lists.newArrayList();
        Map<String, Object> map = Maps.newHashMap();
        map.put("count", totalCount);
        result.add(map);
        return BaseResultGenerator.success(null, null, OutputMode.JSON.getValue(), null, result);
    }

    @Override
    public BaseResult getQueryTimelineByQueryContext(QueryCache queryCache) {
        BaseResult<Object> baseResult = queryCache.getBaseResult();
        return BaseResultGenerator.success(baseResult.getStatistics(), null, baseResult.getOutputMode(), baseResult.getMeta(), baseResult.getData());
    }

    @Autowired
    public void setDatabaseService(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    @Autowired
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Autowired
    public void setSQLSyncQueryService(SQLSyncQueryService sqlSyncQueryService) {
        this.sqlSyncQueryService = sqlSyncQueryService;
    }
}
