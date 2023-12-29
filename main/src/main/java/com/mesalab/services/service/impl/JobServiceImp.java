package com.mesalab.services.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson2.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.*;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.SavedQueryResultUtils;
import com.mesalab.common.utils.sqlparser.SQLFunctionUtil;
import com.mesalab.common.utils.sqlparser.SQLHelper;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.qgw.exception.QGWErrorCode;
import com.mesalab.qgw.model.basic.QueryProfile;
import com.mesalab.qgw.model.basic.HBaseAPISource;
import com.mesalab.qgw.service.QueryService;
import com.mesalab.qgw.service.DatabaseService;
import com.mesalab.services.common.enums.MetricFunction;
import com.mesalab.services.common.enums.MetricType;
import com.mesalab.services.configuration.JobConfig;
import com.mesalab.services.service.JobService;
import com.mesalab.services.common.property.SqlPropertySourceFactory;
import com.geedgenetworks.utils.StringUtil;
import lombok.Data;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.stream.Collectors;


/**
 * TODO
 *
 * @Classname JobServiceImp
 * @Date 2021/11/24 5:12 下午
 * @Author wWei
 */
@Service("jobService")
@PropertySource(value = "classpath:job-sql-template.sql", factory = SqlPropertySourceFactory.class)
public class JobServiceImp implements JobService, EnvironmentAware {
    private static final Log log = LogFactory.get();
    private static final String URL_PREFIX = "jdbc:phoenix:";
    @Autowired
    QueryService queryService;
    @Autowired
    DatabaseService databaseService;
    @Autowired
    JobExecuteService jobExecuteService;
    @Autowired
    JobConfig jobCfg;
    private Environment env;

    @Autowired
    private HBaseAPISource hBaseAPISourceCache;

    private static HBaseAPISource hBaseAPISource;

    @PostConstruct
    public void init() {
        hBaseAPISource = this.hBaseAPISourceCache;
    }

    public static Properties PROPERTIES = new Properties();

    static {
        try {
            Class.forName("org.apache.phoenix.jdbc.PhoenixDriver");
            PROPERTIES.setProperty("phoenix.schema.isNamespaceMappingEnabled", "true");
            PROPERTIES.setProperty("phoenix.schema.mapSystemTablesToNamespace", "true");
            PROPERTIES.setProperty("zookeeper.recovery.retry.intervalmill", "200");
            PROPERTIES.setProperty("zookeeper.recovery.retry", "3");
            PROPERTIES.setProperty("hbase.client.retries.number", "3");
            PROPERTIES.setProperty("hbase.client.keyvalue.maxsize", "209715200");
        } catch (ClassNotFoundException e) {
            log.error("PhoenixDriver load error. ", e);
        }
    }


    @Override
    public BaseResult commitAdHocQuery(HashMap<String, Object> reqBody) {
        validateAdHocQuery(reqBody);
        String id = DigestUtil.md5Hex(String.valueOf(reqBody));
        String queryType = String.valueOf(reqBody.get(JobConfig.KEY_QUERY_TYPE));
        if (isExist(id)) {
            log.info("Hoc Job already exist, ID is {}, params is {}", id, JSON.toJSONString(reqBody));
            return BaseResultGenerator.successCreate(buildJobResource(id));
        }
        log.info("Add Hoc Job, ID is {}, params is {}", id, JSON.toJSONString(reqBody));
        Map<String, Object> property = Maps.newLinkedHashMap();
        if (JobConfig.FIELD_DISCOVERY.equals(queryType)) {
            initFieldDiscoveryJob(id, reqBody, property);
            jobExecuteService.addExecutorFieldDiscovery(id, reqBody);
        } else if (JobConfig.STATISTICS.equals(queryType)) {
            initStatisticsJob(id, reqBody, property);
            jobExecuteService.addExecutorStatistics(id, reqBody, property);
        }
        return BaseResultGenerator.successCreate(buildJobResource(id));
    }

    private void initStatisticsJob(String id, HashMap<String, Object> reqBody, Map<String, Object> property) {
        buildPropertyOfStatistics(String.valueOf(reqBody.get(JobConfig.KEY_CUSTOM_STATISTICS_SQL)), property);
        if (!isValidProperty(property)) {
            property.remove(JobConfig.JOB_PROPERTY_K_OPTION);
        }
        execute(String.format(Objects.requireNonNull(env.getProperty("JOB_INIT")), id, false, 0, false, false, System.currentTimeMillis(), JSON.toJSONString(property), 0, 0));
    }

    private boolean isValidProperty(Map<String, Object> property) {
        if (!property.containsKey(JobConfig.JOB_PROPERTY_K_OPTION)) {
            return false;
        }
        String option = String.valueOf(property.get(JobConfig.JOB_PROPERTY_K_OPTION));
        if (StrUtil.equalsIgnoreCase(option, JobConfig.JOB_PROPERTY_V_OPTION_TOP)
                || StrUtil.equals(option, JobConfig.JOB_PROPERTY_V_OPTION_RARE)) {
            SubOption subOption = JSONUtil.toBean(JSONUtil.toJsonStr(property.get(JobConfig.JOB_PROPERTY_K_SUB_OPTION)), SubOption.class);
            if (subOption.dimension.isEmpty() || subOption.metric.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public BaseResult commitSavedQuery(HashMap<String, Object> map) {
        validateReport(map);
        String sql = JSONUtil.quote(map.get(JobConfig.KEY_CUSTOM_SAVED_QUERY_SQL).toString(), false).replace("'", "\\'");
        String id = getSameJob(sql);
        if (StringUtil.isNotBlank(id)) {
            return BaseResultGenerator.successCreate(buildJobResource(id));
        }
        map.put("UUID", System.currentTimeMillis());
        id = DigestUtil.md5Hex(String.valueOf(map));
        long currentTime = System.currentTimeMillis() / 1000;
        Db.update(String.format(Objects.requireNonNull(env.getProperty("SAVED_QUERY_JOB_INIT")), id, sql, currentTime, currentTime));
        return BaseResultGenerator.successCreate(buildJobResource(id));
    }

    @Override
    public BaseResult getSavedQueryStatus(String jobId) {
        Map<String, Object> job = buildJobStatus(jobId);
        return BaseResultGenerator.success("ok", Lists.newArrayList(job));
    }

    private Map<String, Object> buildJobStatus(String jobId) {
        List<Object> query = Db.query(String.format(Objects.requireNonNull(env.getProperty("SAVED_QUERY_JOB_STATUS")), jobId, 1));
        if (CollectionUtil.isEmpty(query)) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(), "No job found with the given ID");
        }
        return buildJobStatus(Arrays.asList((Object[]) query.get(0)));
    }

    @Override
    public BaseResult getSavedQueryResult(String jobId) {
        BaseResult statusBaseResult = getSavedQueryStatus(jobId);
        if (statusBaseResult.isSuccess()) {
            List<HashMap<String, Object>> statusData = (List<HashMap<String, Object>>) statusBaseResult.getData();
            HashMap<String, Object> jobInfo = statusData.get(0);
            boolean isDone = Boolean.parseBoolean(jobInfo.get(JobConfig.IS_DONE).toString());
            boolean isCanceled = Boolean.parseBoolean(jobInfo.get(JobConfig.IS_CANCELED).toString());
            boolean isFailed = Boolean.parseBoolean(jobInfo.get(JobConfig.IS_FAILED).toString());
            if (!isCanceled && isDone && !isFailed) {
                List<BaseResult> resultList = buildBaseResults(Lists.newArrayList(jobId), Lists.newArrayList(jobInfo));
                return resultList.get(0);
            } else {
                return BaseResultGenerator.success(null, jobInfo, null, null);
            }
        }
        throw new BusinessException(ResultStatusEnum.SERVER_ERROR.getCode(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(),
                String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(), "Get job status error: " + statusBaseResult.getMessage()));
    }

    private List<BaseResult> buildBaseResults(List<String> ids, List<Map<String, Object>> jobStatus) {
        BaseResult<Object> resultBaseResult = SavedQueryResultUtils.getByRowKey(ids);
        if (!resultBaseResult.isSuccess()) {
            throw new BusinessException(ResultStatusEnum.SERVER_ERROR.getCode(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(),
                    String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(), "Get job result error: " + resultBaseResult.getMessage()));
        }
        List<Map<String, Object>> resultData = (List<Map<String, Object>>) resultBaseResult.getData();
        List<BaseResult> resultList = Lists.newArrayList();
        for (Map<String, Object> data : resultData) {
            BaseResult result;
            Map map = JSON.parseObject((String) data.get("result"), Map.class);
            result = BaseResultGenerator.generate(Integer.parseInt(String.valueOf(map.get("status"))), ResultCodeEnum.SUCCESS.getCode(), String.valueOf(map.get("message")),
                    map.get("data"), map.get("meta"), (Map<String, Object>) (map.get(JobConfig.STATISTICS)), OutputModeEnum.JSON.getValue());
            String rowKey = String.valueOf(data.get("ROWKEY"));
            jobStatus.forEach(o -> {
                if (StrUtil.equals(rowKey, String.valueOf(o.get(JobConfig.JOB_ID)))) {
                    result.setJob(o);
                }
            });
            resultList.add(result);
        }
        return resultList;
    }

    @Override
    public BaseResult cancelSavedQuery(String jobId) {
        Db.update(String.format(Objects.requireNonNull(env.getProperty("SAVED_QUERY_JOB_UPDATE")), "is_valid", 0, jobId));
        return BaseResultGenerator.success(ResultStatusEnum.ACCEPTED.getCode(), "ok", null);
    }

    @Override
    public List<Map<String, Object>> batchSavedQueryStatus(List<String> ids) {
        List<Object> data = Db.query(String.format(Objects.requireNonNull(env.getProperty("SAVED_QUERY_JOB_STATUS")), StrUtil.join("', '", ids), ids.size()));
        List<Map<String, Object>> result = Lists.newArrayList();
        data.forEach(x -> {
            Map<String, Object> job = buildJobStatus(Arrays.asList((Object[]) x));
            result.add(job);
        });
        if (result.size() != ids.size()) {
            List<String> resultIds = Lists.newArrayList();
            result.forEach(o -> resultIds.add(String.valueOf(o.get(JobConfig.JOB_ID))));
            List<String> collect = ids.stream().filter(item -> !resultIds.contains(item)).collect(Collectors.toList());
            throw new BusinessException(ResultStatusEnum.NOT_FOUND.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), "Not Find Jobs: " + StrUtil.join(",", collect)));
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> batchSavedQueryResult(List<String> ids) {
        List<Map<String, Object>> statusList = batchSavedQueryStatus(ids);
        if (!statusList.isEmpty()) {
            List<BaseResult> dataList = buildBaseResults(Lists.newArrayList(ids), statusList);
            List<Map<String, Object>> resultList = Lists.newArrayList();
            List<Map<String, Object>> statusListTmp = Lists.newArrayList(statusList);
            for (BaseResult item : dataList) {
                Map<String, Object> result = Maps.newLinkedHashMap();
                result.put(JobConfig.JOB, item.getJob());
                statusListTmp.remove(item.getJob());
                result.put(JobConfig.STATISTICS, item.getStatistics());
                result.put("meta", item.getMeta());
                result.put("data", item.getData());
                resultList.add(result);
            }
            for (Map<String, Object> map : statusListTmp) {
                Map<String, Object> result = Maps.newLinkedHashMap();
                result.put(JobConfig.JOB, map);
                result.put(JobConfig.STATISTICS, null);
                result.put("meta", null);
                result.put("data", null);
                resultList.add(result);
            }
            return resultList;
        }
        throw new BusinessException(ResultStatusEnum.SERVER_ERROR.getCode(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(),
                String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(), "Get job status error: " + statusList));
    }

    @Override
    public BaseResult getResultFieldDiscovery(String id) {
        return buildResultFieldDiscovery(id);
    }

    @Override
    public BaseResult longTermResult(String id) {
        return buildLongTermResult(id);
    }


    private Map<String, Object> buildJobStatus(List<Object> list) {
        Map<String, Object> job = Maps.newLinkedHashMap();
        job.put(JobConfig.JOB_ID, list.get(8));
        job.put("is_done", "DONE".equalsIgnoreCase(String.valueOf(list.get(0))));
        job.put("done_progress", Math.min(Double.parseDouble(String.valueOf(list.get(1))), 1));
        job.put("is_canceled", !BooleanUtil.toBoolean(String.valueOf(list.get(3))));
        job.put("is_failed", BooleanUtil.toBoolean(String.valueOf(list.get(2))));
        job.put("start_time", list.get(4));
        job.put("end_time", list.get(5));
        job.put("count", list.get(6));
        return job;
    }

    private BaseResult buildLongTermResult(String id) {
        List<Map<String, Object>> data = validationData(id);
        updateQueryTime(id, System.currentTimeMillis());
        Map<String, Object> job = Maps.newHashMap();
        Map<String, Object> map = data.get(0);
        List<Map<String, Object>> resultList = Lists.newArrayList();
        List<Map<String, Object>> meta = Lists.newArrayList();
        Object item = map.get(JobConfig.LONG_TERM_RESULT);
        Object property = map.get(JobConfig.JOB_PROPERTY);
        if (StringUtil.isNotEmpty(item) && StringUtil.isNotEmpty(property)) {
            try {
                Map<String, List<Map<String, Object>>> result = JSONUtil.toBean(JSONUtil.escape(item.toString()), Map.class);
                resultList = result.get("data");

                Map<String, Object> propertyMap = JSONUtil.toBean(JSONUtil.escape(property.toString()), Map.class);
                if (StringUtil.isNotEmpty(propertyMap.get(JobConfig.JOB_PROPERTY_K_SUB_OPTION))) {
                    Map<String, Object> subOption = JSONUtil.toBean(JSONUtil.escape(propertyMap.get(JobConfig.JOB_PROPERTY_K_SUB_OPTION).toString()), Map.class);
                    if (StringUtil.isNotEmpty(subOption.get(JobConfig.JOB_PROPERTY_LIMIT))) {
                        int i = Integer.parseInt(subOption.get(JobConfig.JOB_PROPERTY_LIMIT).toString());
                        resultList = resultList.size() <= i ? resultList : resultList.subList(0, i);
                    }
                }
                meta = result.get("meta");
            } catch (RuntimeException e) {
                log.error("parse jobId-{}, field-{} error, message is: {}", JobConfig.LONG_TERM_RESULT, id, e.getMessage());
            }
        }
        HashMap<String, Object> statistics = Maps.newHashMap();
        statistics.put("elapsed", map.get(JobConfig.DURATION_TIME));
        statistics.put("rows_read", resultList.size());
        statistics.put("result_rows", resultList.size());
        statistics.put("result_bytes", String.valueOf(resultList).getBytes().length);
        job.put(JobConfig.COUNT, StringUtil.isEmpty(map.get(JobConfig.COUNT)) ? 0 : map.get(JobConfig.COUNT));
        job.put(JobConfig.IS_DONE, StringUtil.isEmpty(map.get(JobConfig.IS_DONE)) ? false : map.get(JobConfig.IS_DONE));
        job.put(JobConfig.DONE_PROGRESS, StringUtil.isEmpty(map.get(JobConfig.DONE_PROGRESS)) ? 0 : map.get(JobConfig.DONE_PROGRESS));
        job.put(JobConfig.IS_CANCELED, StringUtil.isEmpty(map.get(JobConfig.IS_CANCELED)) ? false : map.get(JobConfig.IS_CANCELED));
        job.put(JobConfig.IS_FAILED, StringUtil.isEmpty(map.get(JobConfig.IS_FAILED)) ? false : map.get(JobConfig.IS_FAILED));
        return BaseResultGenerator.success(statistics, job, meta, resultList);
    }

    private BaseResult<List<Map<String, Object>>> buildResultFieldDiscovery(String id) {
        List<Map<String, Object>> data = validationData(id);
        updateQueryTime(id, System.currentTimeMillis());
        Map<String, Object> job = Maps.newHashMap();
        Map<String, Object> map = data.get(0);
        Map jobProperty = JSON.parseObject(map.get(JobConfig.JOB_PROPERTY).toString(), Map.class);
        Object fields = jobProperty.get(JobConfig.JOB_PROPERTY_K_RESULT_FIELD);
        String[] list = StringUtil.isEmpty(fields) ? new String[0] : fields.toString().split(",");
        List<Map<String, Object>> resultList = Lists.newArrayList();
        for (String field : list) {
            Map<String, Object> result = Maps.newHashMap();
            Object item = map.get(field);
            if (StringUtil.isEmpty(item)) {
                continue;
            }
            try {
                Map fieldResult = JSONUtil.toBean(JSONUtil.escape(item.toString()), Map.class);
                List<Object> topK = (List<Object>) fieldResult.get("topk");
                fieldResult.put("topk", topK.size() > 10 ? topK.subList(0, 10) : topK);
                result.put(field, fieldResult);
                resultList.add(result);
            } catch (RuntimeException e) {
                log.error("parse jobId-{}, field-{} error, message is: {}", field, id, e.getMessage());
            }
        }
        HashMap<String, Object> statistics = Maps.newHashMap();
        statistics.put("elapsed", map.get(JobConfig.DURATION_TIME));
        statistics.put("rows_read", resultList.size());
        statistics.put("result_rows", resultList.size());
        statistics.put("result_bytes", String.valueOf(resultList).getBytes().length);
        job.put(JobConfig.COUNT, StringUtil.isEmpty(map.get(JobConfig.COUNT)) ? 0 : map.get(JobConfig.COUNT));
        job.put(JobConfig.IS_DONE, StringUtil.isEmpty(map.get(JobConfig.IS_DONE)) ? false : map.get(JobConfig.IS_DONE));
        job.put(JobConfig.DONE_PROGRESS, StringUtil.isEmpty(map.get(JobConfig.DONE_PROGRESS)) ? 0 : map.get(JobConfig.DONE_PROGRESS));
        job.put(JobConfig.IS_CANCELED, StringUtil.isEmpty(map.get(JobConfig.IS_CANCELED)) ? false : map.get(JobConfig.IS_CANCELED));
        job.put(JobConfig.IS_FAILED, StringUtil.isEmpty(map.get(JobConfig.IS_FAILED)) ? false : map.get(JobConfig.IS_FAILED));
        return BaseResultGenerator.success(statistics, job, null, resultList);
    }

    private boolean isExist(String id) {
        String sql = String.format(Objects.requireNonNull(env.getProperty("JOB_DETAIL")), id);
        BaseResult baseResult = queryService.executeQuery(QueryProfile.builder().query(sql).build());
        if (!baseResult.isSuccess()) {
            log.error("Query job status error: {}", baseResult.getMessage());
            throw new QGWBusinessException(ResultStatusEnum.SERVER_ERROR.getCode(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), "Query job status error: " + baseResult.getMessage());
        }
        List<Map<String, Object>> data = (List<Map<String, Object>>) baseResult.getData();
        Map<String, Object> map;
        if (StringUtil.isEmpty(data) || StringUtil.isEmpty(map = data.get(0)) || StringUtil.isEmpty(map.get(JobConfig.IS_CANCELED))) {
            return false;
        }
        long lastQuery = StringUtil.isEmpty(map.get(JobConfig.LAST_QUERY_TIME)) ? 0 : Long.parseLong(map.get(JobConfig.LAST_QUERY_TIME).toString());
        if (BooleanUtil.toBoolean(String.valueOf(map.get(JobConfig.IS_FAILED)))
                && (System.currentTimeMillis() - lastQuery) > jobCfg.getInteractiveTimeout()) {
            execute(String.format(Objects.requireNonNull(env.getProperty("JOB_DELETE")), id));
            return false;
        }
        return !BooleanUtil.toBoolean(String.valueOf(map.get(JobConfig.IS_CANCELED)));
    }

    private void initFieldDiscoveryJob(String id, HashMap<String, Object> body, Map<String, Object> property) {
        property.put(JobConfig.JOB_PROPERTY_TYPE, JobConfig.FIELD_DISCOVERY);
        List<String> fields = (List<String>) body.get(JobConfig.KEY_CUSTOM_FIELD_DISCOVERY_FIELDS);
        List<String> interim = Lists.newArrayList(fields);
        BaseResult baseResult = queryService.executeQuery(QueryProfile.builder().query(env.getProperty("JOB_GET_FIELD_DISCOVERY_ALL_COLUMN")).option(QueryOptionEnum.REAL_TIME.getValue()).build());
        if (!baseResult.isSuccess()) {
            throw new QGWBusinessException(ResultStatusEnum.SERVER_ERROR.getCode(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(),
                    "Init job error: get field_discovery all column error: ".concat(baseResult.getMessage()));
        }
        List<Map<String, Object>> meta = (List<Map<String, Object>>) baseResult.getMeta();
        List<String> metaName = new ArrayList<>();
        meta.forEach(m -> metaName.add(m.get("name").toString()));
        interim.removeAll(metaName);
        for (String field : interim) {
            execute(String.format(Objects.requireNonNull(env.getProperty("JOB_ADD_COLUMN")), JobConfig.FIELD_DISCOVERY, field));
        }
        property.put(JobConfig.JOB_PROPERTY_K_RESULT_FIELD, String.join(",", fields));
        execute(String.format(Objects.requireNonNull(env.getProperty("JOB_INIT")), id, false, 0, false, false, System.currentTimeMillis(), JSON.toJSONString(property), 0, 0));
    }

    private void buildPropertyOfStatistics(String sql, Map<String, Object> property) {
        property.put(JobConfig.JOB_PROPERTY_TYPE, JobConfig.STATISTICS);
        property.put(JobConfig.JOB_PROPERTY_K_RESULT_FIELD, JobConfig.LONG_TERM_RESULT);
        try {
            PlainSelect plainSelect = checkParam(sql, CCJSqlParserUtil.parse(sql));
            PlainSelect plainSelectInner = SQLHelper.getSelectInner(sql);

            if (StringUtil.isNotEmpty(plainSelect.getOffset())
                    || (StringUtil.isNotEmpty(plainSelect.getLimit()) && StringUtil.isNotEmpty(plainSelect.getLimit().getOffset()))) {
                return;
            }
            if (StringUtil.isEmpty(plainSelect.getOrderByElements()) || StringUtil.isEmpty(plainSelect.getGroupBy().getGroupByExpressionList())) {
                return;
            }
            if (StringUtil.isEmpty(plainSelect.getGroupBy())
                    || StringUtil.isEmpty(plainSelect.getGroupBy().getGroupByExpressionList())
                    || StringUtil.isEmpty(plainSelect.getGroupBy().getGroupByExpressionList().getExpressions())
                    || StringUtil.isEmpty(plainSelectInner.getGroupBy())) {
                return;
            }

            SubOption subOption = new SubOption();
            Optional.ofNullable(plainSelect.getLimit()).ifPresent(x -> subOption.setLimit(Long.parseLong(String.valueOf(x.getRowCount()))));

            Map<String, Object> aliasExpr = Maps.newHashMap();
            for (SelectItem item : plainSelect.getSelectItems()) {
                if (!(item instanceof SelectExpressionItem)) {
                    return;
                }
                SelectExpressionItem selectExprItem = (SelectExpressionItem) item;
                if (StringUtil.isEmpty(selectExprItem.getAlias())) {
                    aliasExpr.put(String.valueOf(selectExprItem.getExpression()), selectExprItem.getExpression());
                } else {
                    aliasExpr.put(selectExprItem.getAlias().getName(), selectExprItem.getExpression());
                }
            }
            for (Expression groupItem : plainSelect.getGroupBy().getGroupByExpressionList().getExpressions()) {
                if (aliasExpr.keySet().stream().noneMatch(o -> o.equals(String.valueOf(groupItem)))
                        && aliasExpr.values().stream().noneMatch(o -> o.equals(String.valueOf(groupItem)))) {
                    return;
                }
            }
            List<Expression> groupByItems = plainSelect.getGroupBy().getGroupByExpressionList().getExpressions();
            for (String key : aliasExpr.keySet()) {
                Object value = aliasExpr.get(key);
                if (groupByItems.stream().anyMatch(groupItem -> (key.equals(groupItem.toString()) || (value.toString().equals(groupItem.toString()))))) {
                    subOption.getDimension().add(key);
                    continue;
                }
                if (value instanceof Function) {
                    Function fun = ((Function) value);
                    String name = fun.getName().toLowerCase();
                    if (!SQLFunctionUtil.timeSlicingFunction.containsKey(name)) {
                        return;
                    }
                    if (fun.isDistinct() || fun.isUnique()) {
                        return;
                    }
                    subOption.getMetric().put(key, SQLFunctionUtil.timeSlicingFunction.get(name));
                }
            }
            for (OrderByElement orderByElement : plainSelect.getOrderByElements()) {
                String sort = String.valueOf(orderByElement.getExpression());
                boolean inSelectItem = aliasExpr.keySet().stream().anyMatch(key -> {
                    if (sort.equals(key) || sort.equals(aliasExpr.get(key))) {
                        subOption.getIsAsc().put(key, orderByElement.isAsc());
                        return true;
                    } else {
                        return false;
                    }
                });
                if (!inSelectItem) {
                    return;
                }
            }
            property.put(JobConfig.JOB_PROPERTY_K_SUB_OPTION, subOption);

            if (subOption.getIsAsc().keySet().size() != 1) {
                return;
            }
            if (isTimechart(sql, aliasExpr, subOption)) {
                property.put(JobConfig.JOB_PROPERTY_K_OPTION, JobConfig.JOB_PROPERTY_V_OPTION_TIMECHART);
                return;
            }
            // top、rare
            for (Object key : aliasExpr.keySet()) {
                Object value = aliasExpr.get(key);
                if (value instanceof Function) {
                    Function fun = (Function) value;
                    if (!(SQLFunctionUtil.timeSlicingFunction.containsKey(fun.getName().toLowerCase()))) {
                        return;
                    }
                    if (!(subOption.getIsAsc().containsKey(value) || subOption.getIsAsc().containsKey(key))) {
                        return;
                    }
                } else {
                    if (!(subOption.getDimension().contains(value) || subOption.getDimension().contains(key))) {
                        return;
                    }
                }
            }
            if (subOption.getIsAsc().values().stream().findFirst().get()) {
                property.put(JobConfig.JOB_PROPERTY_K_OPTION, JobConfig.JOB_PROPERTY_V_OPTION_RARE);
            } else {
                property.put(JobConfig.JOB_PROPERTY_K_OPTION, JobConfig.JOB_PROPERTY_V_OPTION_TOP);
            }
        } catch (JSQLParserException | RuntimeException e) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), "long_term job init error: ".concat(e.getMessage())));
        }
    }

    private boolean isTimechart(String sql, Map<String, Object> aliasExpr, SubOption subOption) {
        boolean isTimechart = false;
        for (Object key : aliasExpr.keySet()) {
            Object expr = aliasExpr.get(key);
            if ((expr instanceof Function) && (SQLFunctionUtil.timeSlicingFunction.containsKey(((Function) expr).getName().toLowerCase()))) {
                continue;
            }
            if ((expr instanceof Function) && (SQLFunctionUtil.TIME_FLOOR_WITH_FILL.equals(((Function) expr).getName()))
                    && (subOption.getDimension().size() == 1 && ((subOption.getDimension().contains(key)) || subOption.getDimension().contains(expr))
                    && (subOption.getIsAsc().containsKey(key) || subOption.getIsAsc().containsKey(expr)))) {
                List<Expression> timeFloorWithFill = SQLHelper.getFunctionParams(sql, SQLFunctionUtil.TIME_FLOOR_WITH_FILL);
                if (timeFloorWithFill.size() >= 2) {
                    Matcher matcherPT = SQLFunctionUtil.periodOfPT.matcher(String.valueOf(timeFloorWithFill.get(1)));
                    if (matcherPT.find()) {
                        long num = Long.parseLong(matcherPT.group(1));
                        String unit = matcherPT.group(2);
                        if ((("S".equalsIgnoreCase(unit) || "M".equalsIgnoreCase(unit)) && (num % 60 == 0) || 60 % num == 0) || ("H".equalsIgnoreCase(unit) && (num % 24 == 0) || 24 % num == 0)) {
                            isTimechart = true;
                            continue;
                        }
                        return false;
                    }
                    return false;
                }
                return false;
            }
            return false;
        }
        if (isTimechart) {
            return true;
        }
        return false;
    }


    private PlainSelect checkParam(String sql, net.sf.jsqlparser.statement.Statement statement) {
        SelectBody selectBody;
        if (!(statement instanceof Select) || !((selectBody = ((Select) statement).getSelectBody()) instanceof PlainSelect)) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), QGWErrorCode.SQL_SYNTAX_PARSE_EXCEPTION.getCode(),
                    String.format(QGWErrorCode.SQL_SYNTAX_PARSE_EXCEPTION.getMessage(), "long_term job init error, not support, SQL is: " + sql));

        }
        PlainSelect plainSelect = (PlainSelect) selectBody;
        PlainSelect plainSelectInner = SQLHelper.getSelectInner(sql);
        if (StringUtil.isEmpty(plainSelect.getGroupBy()) && StringUtil.isEmpty(plainSelectInner.getGroupBy())) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), QGWErrorCode.SQL_SYNTAX_PARSE_EXCEPTION.getCode(),
                    String.format(QGWErrorCode.SQL_SYNTAX_PARSE_EXCEPTION.getMessage(), "long_term job init error, not support non-aggregate SQL is: " + sql));
        }
        return plainSelect;
    }

    private List<Map<String, Object>> validationData(String id) {
        BaseResult baseResult = queryService.executeQuery(
                QueryProfile.builder().option(QueryOptionEnum.REAL_TIME.getValue())
                        .query(String.format(Objects.requireNonNull(env.getProperty("JOB_RESULT")), id))
                        .build());
        if (!baseResult.isSuccess()) {
            throw new QGWBusinessException(ResultStatusEnum.SERVER_ERROR.getCode(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(),
                    String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(), "get Job Result Error: ".concat(baseResult.getMessage())));
        }
        List<Map<String, Object>> data = (List<Map<String, Object>>) baseResult.getData();
        if (StringUtil.isEmpty(data) || StringUtil.isEmpty(data.get(0).get(JobConfig.JOB_PROPERTY))) {
            throw new QGWBusinessException(ResultStatusEnum.NOT_FOUND.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), "Not Found This Job: ".concat(id)));
        }
        return data;
    }

    private void updateQueryTime(String id, long now) {
        executeUpdate(String.format(Objects.requireNonNull(env.getProperty("JOB_UPDATE_FIELD_VALUE")), JobConfig.DETAIL, JobConfig.LAST_QUERY_TIME, id, now));
    }

    private void validateAdHocQuery(HashMap<String, Object> body) {
        Object type = body.get(JobConfig.KEY_QUERY_TYPE);
        if (JobConfig.FIELD_DISCOVERY.equals(type)) {
            validCustomParamsOfFieldDiscovery(body);
            List<String> fields = (List<String>) body.get(JobConfig.KEY_CUSTOM_FIELD_DISCOVERY_FIELDS);
            body.put(JobConfig.KEY_CUSTOM_FIELD_DISCOVERY_FIELDS, fields.stream().distinct().collect(Collectors.toList()));
            syntaxValidationFieldDiscovery(body);
        } else if (JobConfig.STATISTICS.equals(type)) {
            syntaxValidationStatistics(body);
        }
    }

    private void validCustomParamsOfFieldDiscovery(HashMap<String, Object> body) {
        Object metric = body.get(JobConfig.KEY_CUSTOM_FIELD_DISCOVERY_METRIC);
        if (StringUtil.isEmpty(metric)) {
            return;
        }
        boolean validMetric = MetricType.isValid(String.valueOf(metric));
        boolean validFn = MetricFunction.isValid(String.valueOf(body.get(JobConfig.KEY_CUSTOM_FIELD_DISCOVERY_METRIC_FN)));
        if (!validMetric || !validFn) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), QGWMessageConst.QUERY_CUSTOM_FIELD_DISCOVERY_ERROR));
        }
    }

    private void validateReport(HashMap<String, Object> map) {
        QueryProfile queryProfile = QueryProfile.builder().query(String.valueOf(map.get(JobConfig.KEY_CUSTOM_SAVED_QUERY_SQL)))
                .option(QueryOptionEnum.SYNTAX_PARSE.getValue())
                .build();
        BaseResult baseResult = queryService.executeQuery(queryProfile);
        if (!baseResult.isSuccess()) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), baseResult.getMessage()));
        }
    }

    private String getSameJob(String paramSQL) {
        List<Record> jobs = Db.find(String.format(Objects.requireNonNull(env.getProperty("SAVED_QUERY_JOB_SAME")), paramSQL));
        if (jobs.isEmpty()) {
            return StringUtil.EMPTY;
        }
        return jobs.get(0).get("id");
    }

    private void syntaxValidationFieldDiscovery(HashMap<String, Object> map) {
        String syntaxValidation = env.getProperty("SYNTAX_VALIDATION");
        List field = (List) map.get("custom.field_discovery.fields");
        Object filter = map.get("custom.field_discovery.filter");
        String validationSql = String.format(syntaxValidation, String.join(",", field), map.get("query.data_source"), StringUtil.isEmpty(filter) ? " 1 = 1" : filter);
        QueryProfile queryProfile = QueryProfile.builder().query(validationSql).option(QueryOptionEnum.SYNTAX_VALIDATION.getValue()).build();
        BaseResult baseResult = queryService.executeQuery(queryProfile);
        if (!baseResult.isSuccess()) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), baseResult.getMessage()));
        }
    }

    private void syntaxValidationStatistics(HashMap<String, Object> body) {
        String sql = String.valueOf(body.get(JobConfig.KEY_CUSTOM_STATISTICS_SQL));
        BaseResult baseResult = queryService.executeQuery(QueryProfile.builder().query(sql).option(QueryOptionEnum.SYNTAX_VALIDATION.getValue()).build());
        if (!baseResult.isSuccess()) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), baseResult.getMessage()));
        }
    }

    public static boolean executeUpdate(String sql) {
        Connection conn = null;
        java.sql.Statement statement = null;
        int i;
        try {
            log.info("{} engine execute update: {}", DBTypeEnum.HBASE.getValue(), sql);
            PROPERTIES.setProperty("hbase.rpc.timeout", hBaseAPISource.getRpcTimeout());
            PROPERTIES.setProperty("hbase.client.scanner.timeout.period", hBaseAPISource.getRpcTimeout());
            PROPERTIES.setProperty("phoenix.query.timeoutMs", hBaseAPISource.getRpcTimeout());
            String url = URL_PREFIX.concat(hBaseAPISource.getZookeeperQuorum()).concat(":").concat(hBaseAPISource.getZookeeperZnodeParent());
            conn = DriverManager.getConnection(url, PROPERTIES);
            statement = conn.createStatement();
            i = statement.executeUpdate(sql);
            conn.commit();
        } catch (RuntimeException | SQLException e) {
            log.error("{} engine error: {}", DBTypeEnum.HBASE.getValue(), e);
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), QGWErrorCode.SQL_SYNTAX_PARSE_EXCEPTION.getCode(), e.getMessage());
        } finally {
            close(statement, conn);
        }
        return i == 1;
    }

    private Map<String, Object> buildJobResource(String id) {
        Map<String, Object> result = Maps.newHashMap();
        result.put(JobConfig.JOB_ID, id);
        return result;
    }

    public static void execute(String sql) {
        Connection conn = null;
        java.sql.Statement statement = null;
        try {
            log.info("DB Engine is :{}, Execute is: {}", DBTypeEnum.HBASE.getValue(), sql);
            PROPERTIES.setProperty("hbase.rpc.timeout", hBaseAPISource.getRpcTimeout());
            PROPERTIES.setProperty("hbase.client.scanner.timeout.period", hBaseAPISource.getRpcTimeout());
            PROPERTIES.setProperty("phoenix.query.timeoutMs", hBaseAPISource.getRpcTimeout());
            String url = URL_PREFIX.concat(hBaseAPISource.getZookeeperQuorum()).concat(":").concat(hBaseAPISource.getZookeeperZnodeParent());
            conn = DriverManager.getConnection(url, PROPERTIES);
            statement = conn.createStatement();
            statement.execute(sql);
            conn.commit();
        } catch (RuntimeException | SQLException e) {
            log.error("DB Engine is :{}, Query is error: {}", DBTypeEnum.HBASE.getValue(), e);
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), QGWErrorCode.SQL_SYNTAX_PARSE_EXCEPTION.getCode(),
                    String.format(QGWErrorCode.SQL_SYNTAX_PARSE_EXCEPTION.getMessage(), e.getMessage()));


        } finally {
            close(statement, conn);
        }
    }

    private static void close(java.sql.Statement st, Connection conn) {
        if (st != null) {
            try {
                st.close();
            } catch (SQLException e) {
                log.error("Statement close error : ", e.getMessage());
            }

        }
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                log.error("Connection close error : ", e.getMessage());
            }

        }
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.env = environment;
    }

    @Data
    class SubOption {
        private Map<String, String> metric = Maps.newHashMap();
        private List<String> dimension = Lists.newArrayList();
        private Map<String, Boolean> isAsc = Maps.newHashMap();
        private Long limit;
    }
}
