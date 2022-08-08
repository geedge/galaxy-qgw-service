package com.mesalab.services.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayway.jsonpath.JsonPath;
import com.jfinal.plugin.activerecord.Db;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.DBTypeEnum;
import com.mesalab.common.enums.QueryOptionEnum;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.ReportCacheUtils;
import com.mesalab.common.utils.sqlparser.SQLFunctionUtil;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.HBaseAPISource;
import com.mesalab.qgw.service.ApiService;
import com.mesalab.qgw.service.MetadataService;
import com.mesalab.services.configuration.JobConfig;
import com.mesalab.services.service.JobService;
import com.mesalab.services.common.property.SqlPropertySourceFactory;
import com.zdjizhi.utils.DateUtils;
import com.zdjizhi.utils.JsonMapper;
import com.zdjizhi.utils.StringUtil;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.sql.*;
import java.util.*;
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
    ApiService apiService;
    @Autowired
    MetadataService metadataService;
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
        } catch (ClassNotFoundException e) {
            log.error("PhoenixDriver load error. ", e);
        }
    }


    @Override
    public BaseResult commitAdHocQuery(HashMap<String, Object> map) {
        validateAdHocQuery(map);
        String id = DigestUtil.md5Hex16(String.valueOf(map.get(JobConfig.KEY_QUERY_TYPE)).toLowerCase()).concat(JobConfig.DELIMIT).concat(DigestUtil.md5Hex16(String.valueOf(map)));
        if (!isExist(id)) {
            log.info("Add Hoc Job, ID is {}, params is {}", id, JsonMapper.toJsonString(map));
            init(id, map);
            if (JobConfig.FIELD_DISCOVERY.equals(map.get(JobConfig.KEY_QUERY_TYPE))) {
                jobExecuteService.addExecutorFieldDiscovery(id, map);
            } else if (JobConfig.STATISTICS.equals(map.get(JobConfig.KEY_QUERY_TYPE))) {
                jobExecuteService.addExecutorStatistics(id, map);
            }
        }
        return BaseResultGenerator.success("ok", buildData(id));
    }

    @Override
    public BaseResult commitSavedQuery(HashMap<String, Object> map) {
        validateReport(map);
        map.put("UUID", System.currentTimeMillis());
        String id = DigestUtil.md5Hex16(JobConfig.REPORT)
                .concat(JobConfig.DELIMIT)
                .concat(DigestUtil.md5Hex16(String.valueOf(map)));

        String sql = JSONUtil.quote(map.get(JobConfig.KEY_CUSTOM_REPORT_SQL).toString(), false).replace("'", "\\'");
        String currentDate = DateUtils.getCurrentDate(DateUtils.YYYY_MM_DD_HH24_MM_SS);
        sql = String.format(Objects.requireNonNull(env.getProperty("REPORT_INSERT")), currentDate, sql, currentDate, currentDate, currentDate, currentDate, id, TimeZone.getDefault().getID());
        Db.update(sql);
        return BaseResultGenerator.success(ResultStatusEnum.CREATED.getCode(), "ok", buildData(id));
    }

    @Override
    public BaseResult getReportStatus(String jobId) {
        List<Object> query = Db.query(String.format(Objects.requireNonNull(env.getProperty("REPORT_STATUS")), jobId));
        if(CollectionUtil.isEmpty(query)){
            return BaseResultGenerator.failure(ResultStatusEnum.FAIL.getCode(), "not found job");
        }
        List<Object> list = Arrays.asList((Object[]) query.get(0));
        HashMap<String, Object> job = new HashMap<>();
        job.put("is_canceled", !BooleanUtil.toBoolean(String.valueOf(list.get(4))));
        job.put("is_failed", Integer.parseInt(String.valueOf(list.get(0))) > 2);
        job.put("done_progress", Math.min(Double.parseDouble(String.valueOf(list.get(3))) / 100, 1));
        job.put("is_done", Integer.parseInt(String.valueOf(list.get(0))) > 1);
        return BaseResultGenerator.success("ok", Lists.newArrayList(job));
    }

    @Override
    public BaseResult getResultReport(String jobId) {
        BaseResult reportStatus = getReportStatus(jobId);
        BaseResult<Object> result;
        if (reportStatus.isSuccess()) {
            List<HashMap<String, Object>> data = (List<HashMap<String, Object>>) reportStatus.getData();
            HashMap<String, Object> jobInfo = data.get(0);
            boolean isDone = Boolean.parseBoolean(jobInfo.get(JobConfig.IS_DONE).toString());
            boolean isCanceled = Boolean.parseBoolean(jobInfo.get(JobConfig.IS_CANCELED).toString());
            boolean isFailed = Boolean.parseBoolean(jobInfo.get("is_failed").toString());
            if (!isCanceled && isDone && !isFailed) {
                List<Object> query = Db.query(String.format(Objects.requireNonNull(env.getProperty("REPORT_STATUS")), jobId));
                if (query.size() > 0) {
                    Object[] o = (Object[]) query.get(0);
                    List<Object> list = Arrays.asList(o);
                    String resultId = String.valueOf(list.get(1));
                    String querySql = String.valueOf(list.get(2));
                    result = ReportCacheUtils.get(resultId, querySql);
                    result.setJob(jobInfo);
                    return result;
                }
            } else {
                BaseResultGenerator.failure(ResultStatusEnum.FAIL.getCode(), "job unfinished");
            }
        }
        return BaseResultGenerator.failure(ResultStatusEnum.SERVER_ERROR.getCode(), "get job info error");
    }

    @Override
    public BaseResult cancelledReportJob(String jobId) {
        Db.update(String.format(Objects.requireNonNull(env.getProperty("REPORT_UPDATE")), "is_valid", 0, jobId));
        BaseResult<Object> accepted = BaseResultGenerator.success(ResultStatusEnum.ACCEPTED.getCode(), "Accepted", null);
        return BaseResultGenerator.success(ResultStatusEnum.ACCEPTED.getCode(), "Accepted", null);
    }

    @Override
    public BaseResult getResultFieldDiscovery(String id) {
        return buildResultFieldDiscovery(id);
    }

    @Override
    public BaseResult getResultStatistics(String id) {
        return buildResultStatistics(id);
    }

    private BaseResult buildResultStatistics(String id) {
        List<Map<String, Object>> data = validationData(id);
        updateQueryTime(id, System.currentTimeMillis());
        Map<String, Object> job = Maps.newHashMap();
        Map<String, Object> map = data.get(0);
        List<Map<String, Object>> resultList = Lists.newArrayList();
        List<Map<String, Object>> meta = Lists.newArrayList();
        Object item = map.get(JobConfig.STATISTICS_RESULT);
        if (StringUtil.isNotEmpty(item)) {
            try {
                Map<String, List<Map<String, Object>>> result = JSONUtil.toBean(JSONUtil.escape(item.toString()), Map.class);
                resultList = result.get("data");
                 meta = result.get("meta");
                Map map2 = com.mesalab.common.utils.JsonMapper.fromJsonString(map.get(JobConfig.JOB_PROPERTY).toString(), Map.class);

                if (map2.containsKey(JobConfig.JOB_PROPERTY_LIMIT)) {
                    int limit = JsonPath.read(JSONUtil.toJsonStr(map.get(JobConfig.JOB_PROPERTY)), "$.".concat(JobConfig.JOB_PROPERTY_LIMIT));
                    resultList = resultList.size() > limit ? resultList.subList(0, limit) : resultList;
                }
            } catch (RuntimeException e) {
                log.error("parse jobId-{}, field-{} error, message is: {}", JobConfig.STATISTICS_RESULT, id, e.getMessage());
            }
        }

        HashMap<String, Object> statistics = Maps.newHashMap();
        statistics.put("elapsed", map.get(JobConfig.DURATION_TIME));
        statistics.put("rows_read", resultList.size());
        statistics.put("result_rows", resultList.size());
        statistics.put("result_size", String.valueOf(resultList).getBytes().length);
        job.put(JobConfig.COUNT, StringUtil.isEmpty(map.get(JobConfig.COUNT)) ? 0 : map.get(JobConfig.COUNT));
        job.put(JobConfig.IS_DONE, StringUtil.isEmpty(map.get(JobConfig.IS_DONE)) ? false : map.get(JobConfig.IS_DONE));
        job.put(JobConfig.DONE_PROGRESS, StringUtil.isEmpty(map.get(JobConfig.DONE_PROGRESS)) ? 0 : map.get(JobConfig.DONE_PROGRESS));
        job.put(JobConfig.IS_CANCELED, StringUtil.isEmpty(map.get(JobConfig.IS_CANCELED)) ? false : map.get(JobConfig.IS_CANCELED));
        job.put(JobConfig.IS_ERROR, StringUtil.isEmpty(map.get(JobConfig.IS_ERROR)) ? false : map.get(JobConfig.IS_ERROR));
        return BaseResultGenerator.success(statistics, job, meta, resultList);
    }

    private BaseResult<List<Map<String, Object>>> buildResultFieldDiscovery(String id) {
        List<Map<String, Object>> data = validationData(id);
        updateQueryTime(id, System.currentTimeMillis());
        Map<String, Object> job = Maps.newHashMap();
        Map<String, Object> map = data.get(0);
        Map jobProperty = (Map) JsonMapper.fromJsonString(map.get(JobConfig.JOB_PROPERTY).toString(), Map.class);
        Object fields = jobProperty.get(JobConfig.JOB_PROPERTY_RESULT_FIELD);
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
        statistics.put("result_size", String.valueOf(resultList).getBytes().length);
        job.put(JobConfig.COUNT, StringUtil.isEmpty(map.get(JobConfig.COUNT)) ? 0 : map.get(JobConfig.COUNT));
        job.put(JobConfig.IS_DONE, StringUtil.isEmpty(map.get(JobConfig.IS_DONE)) ? false : map.get(JobConfig.IS_DONE));
        job.put(JobConfig.DONE_PROGRESS, StringUtil.isEmpty(map.get(JobConfig.DONE_PROGRESS)) ? 0 : map.get(JobConfig.DONE_PROGRESS));
        job.put(JobConfig.IS_CANCELED, StringUtil.isEmpty(map.get(JobConfig.IS_CANCELED)) ? false : map.get(JobConfig.IS_CANCELED));
        job.put(JobConfig.IS_ERROR, StringUtil.isEmpty(map.get(JobConfig.IS_ERROR)) ? false : map.get(JobConfig.IS_ERROR));
        return BaseResultGenerator.success(statistics, job, null, resultList);
    }

    private boolean isExist(String id) {
        String sql = String.format(Objects.requireNonNull(env.getProperty("JOB_DETAIL")), id);
        BaseResult baseResult = apiService.executeQuery(ApiParam.builder().query(sql).build());
        if (!baseResult.isSuccess()) {
            log.error("Query job status error: {}", baseResult.getMessage());
            throw new BusinessException(ResultStatusEnum.SERVER_ERROR.getCode(), ResultCodeEnum.UNKNOW_ERROR.getCode(), "Query job status error: " + baseResult.getMessage(), null);
        }
        List<Map<String, Object>> data = (List<Map<String, Object>>) baseResult.getData();
        Map<String, Object> map;
        if (StringUtil.isEmpty(data) || StringUtil.isEmpty(map = data.get(0)) || StringUtil.isEmpty(map.get(JobConfig.IS_CANCELED))) {
            return false;
        }
        long lastQuery = StringUtil.isEmpty(map.get(JobConfig.LAST_QUERY_TIME)) ? 0 : Long.parseLong(map.get(JobConfig.LAST_QUERY_TIME).toString());
        if (BooleanUtil.toBoolean(String.valueOf(map.get(JobConfig.IS_ERROR)))
                && (System.currentTimeMillis() - lastQuery) > jobCfg.getInteractiveTimeout()) {
            execute(String.format(Objects.requireNonNull(env.getProperty("JOB_DELETE")), id));
            return false;
        }
        return !BooleanUtil.toBoolean(String.valueOf(map.get(JobConfig.IS_CANCELED)));
    }

    private void init(String id, HashMap<String, Object> body) {
        Object type = body.get(JobConfig.KEY_QUERY_TYPE);
        if (JobConfig.FIELD_DISCOVERY.equals(type)) {
            initFieldDiscoveryJob(id, body);
        } else if (JobConfig.STATISTICS.equals(type)) {
            initStatisticsJob(id, body);
        }
    }

    private void initStatisticsJob(String id, HashMap<String, Object> body) {
        Map<String, Object> property = Maps.newLinkedHashMap();
        property.put(JobConfig.JOB_PROPERTY_TYPE, JobConfig.STATISTICS);
        buildJobPropertyStatistics(String.valueOf(body.get(JobConfig.KEY_CUSTOM_STATISTICS_SQL)), property);
        execute(String.format(Objects.requireNonNull(env.getProperty("JOB_INIT")), id, false, 0, false, false, System.currentTimeMillis(), JsonMapper.toJsonString(property), 0, 0));
    }

    private void initFieldDiscoveryJob(String id, HashMap<String, Object> body) {
        List<String> fields = (List<String>) body.get(JobConfig.KEY_CUSTOM_FIELD_DISCOVERY_FIELDS);
        List<String> interim = Lists.newArrayList(fields);
        BaseResult baseResult = apiService.executeQuery(ApiParam.builder().query(env.getProperty("JOB_GET_FIELD_DISCOVERY_ALL_COLUMN")).option(QueryOptionEnum.REAL_TIME.getValue()).build());
        if (!baseResult.isSuccess()) {
            throw new BusinessException(ResultStatusEnum.SERVER_ERROR.getCode(), ResultCodeEnum.UNKNOW_ERROR.getCode(),
                    "Init job error: get field_discovery all column error, ".concat(baseResult.getMessage()), null);
        }
        List<Map<String, Object>> meta = (List<Map<String, Object>>) baseResult.getMeta();
        List<String> metaName = new ArrayList<>();
        meta.forEach(m -> metaName.add(m.get("name").toString()));
        interim.removeAll(metaName);
        for (String field : interim) {
            execute(String.format(Objects.requireNonNull(env.getProperty("JOB_ADD_COLUMN")), JobConfig.FIELD_DISCOVERY, field));
        }
        Map<String, Object> param = Maps.newLinkedHashMap();
        param.put(JobConfig.JOB_PROPERTY_TYPE, JobConfig.FIELD_DISCOVERY);
        param.put(JobConfig.JOB_PROPERTY_RESULT_FIELD, String.join(",", fields));
        execute(String.format(Objects.requireNonNull(env.getProperty("JOB_INIT")), id, false, 0, false, false, System.currentTimeMillis(), JsonMapper.toJsonString(param), 0, 0));
    }

    private void buildJobPropertyStatistics(String sql, Map<String, Object> property) {
        net.sf.jsqlparser.statement.Statement statement = null;
        try {
            statement = CCJSqlParserUtil.parse(sql);
            if (statement instanceof Select && ((Select) statement).getSelectBody() instanceof PlainSelect) {
                PlainSelect plainSelect = (PlainSelect) ((Select) statement).getSelectBody();

                Limit limit = plainSelect.getLimit();
                if (StringUtil.isNotEmpty(limit) && limit.getRowCount() instanceof LongValue) {
                    property.put(JobConfig.JOB_PROPERTY_LIMIT, Long.valueOf(limit.getRowCount().toString()));
                }

                List<SelectItem> selectItems = plainSelect.getSelectItems();
                Map<String, String> fieldAlias = Maps.newHashMap();/** k=column,v=alias **/
                selectItems.forEach(o -> {
                    if (o instanceof SelectExpressionItem) {
                        SelectExpressionItem selectExpressionItem = (SelectExpressionItem) o;
                        Expression expression = selectExpressionItem.getExpression();
                        Alias alias = selectExpressionItem.getAlias();
                        fieldAlias.put(expression.toString(), StringUtil.isEmpty(alias) ? expression.toString() : alias.getName());
                    }
                });

                property.put(JobConfig.isASC, true);
                List<OrderByElement> orderByElements = plainSelect.getOrderByElements();
                if (StringUtil.isNotEmpty(orderByElements)) {
                    OrderByElement orderByElement = orderByElements.get(0);
                    property.put(JobConfig.isASC, orderByElement.isAsc());
                    String orderBy = orderByElement.getExpression().toString();
                    property.put(JobConfig.JOB_PROPERTY_ORDER, fieldAlias.containsKey(orderBy) ? fieldAlias.get(orderBy) : orderBy);
                }


                //timeChart
                List<SelectItem> functions = selectItems.stream().filter(o -> ((o instanceof SelectExpressionItem) && (((SelectExpressionItem) o).getExpression()) instanceof Function)).collect(Collectors.toList());
                for (SelectItem selectItem : functions) {
                    SelectExpressionItem select = ((SelectExpressionItem) selectItem);
                    Expression expression = select.getExpression();
                    if (expression instanceof Function) {
                        Function function = ((Function) (expression));
                        buildTimeChart(property, fieldAlias.get(select.getExpression().toString()), function);
                    }
                }
                //top rare
                List<SelectItem> column = selectItems.stream().filter(o -> ((o instanceof SelectExpressionItem) && (((SelectExpressionItem) o).getExpression()) instanceof Column)).collect(Collectors.toList());
                for (SelectItem selectItem : column) {
                    SelectExpressionItem select = (SelectExpressionItem) selectItem;
                    if (BooleanUtil.toBoolean(property.get(JobConfig.isASC).toString())) {
                        property.put(JobConfig.JOB_PROPERTY_SUB_TYPE, JobConfig.JOB_PROPERTY_SUB_TYPE_RARE);
                    } else {
                        property.put(JobConfig.JOB_PROPERTY_SUB_TYPE, JobConfig.JOB_PROPERTY_SUB_TYPE_TOP);
                    }
                    property.put(JobConfig.JOB_PROPERTY_RESULT_FIELD, fieldAlias.get(select.getExpression().toString()));
                }
            }
        } catch (JSQLParserException | RuntimeException e) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                    "Job Validation Error: ".concat(e.getMessage()), null);
        }
    }

    private void buildTimeChart(Map<String, Object> param, String name, Function function) {
        if (SQLFunctionUtil.TIME_FLOOR_WITH_FILL.equals(function.getName())) {
            param.put(JobConfig.JOB_PROPERTY_SUB_TYPE, JobConfig.JOB_PROPERTY_SUB_TYPE_TIMECHART);
            param.put(JobConfig.JOB_PROPERTY_RESULT_FIELD, name);
        } else {
            if (StringUtil.isEmpty(function.getParameters())) {
                return;
            }
            List<Expression> expressions = function.getParameters().getExpressions();
            for (Expression expression : expressions) {
                if (expression instanceof Function) {
                    buildTimeChart(param, name, (Function) expression);
                }
            }
        }
    }

    private List<Map<String, Object>> validationData(String id) {
        BaseResult baseResult = apiService.executeQuery(
                ApiParam.builder().option(QueryOptionEnum.REAL_TIME.getValue())
                        .query(String.format(Objects.requireNonNull(env.getProperty("JOB_RESULT")), id))
                        .build());
        if (!baseResult.isSuccess()) {
            throw new BusinessException(ResultStatusEnum.SERVER_ERROR.getCode(), ResultCodeEnum.UNKNOW_ERROR.getCode(),
                    "get Job Result Error: ".concat(baseResult.getMessage()), null);
        }
        List<Map<String, Object>> data = (List<Map<String, Object>>) baseResult.getData();
        if (StringUtil.isEmpty(data) || StringUtil.isEmpty(data.get(0).get(JobConfig.JOB_PROPERTY))) {
            throw new BusinessException(ResultStatusEnum.NOT_FOUND.getCode(), ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                    "Not Found This Job: ".concat(id), null);
        }
        return data;
    }

    private void updateQueryTime(String id, long now) {
        executeUpdate(String.format(Objects.requireNonNull(env.getProperty("JOB_UPDATE_FIELD_VALUE")), JobConfig.DETAIL, JobConfig.LAST_QUERY_TIME, id, now));
    }

    private void validateAdHocQuery(HashMap<String, Object> body) {
        Object type = body.get(JobConfig.KEY_QUERY_TYPE);
        if (JobConfig.FIELD_DISCOVERY.equals(type)) {
            syntaxValidationFieldDiscovery(body);
        } else if (JobConfig.STATISTICS.equals(type)) {
            syntaxValidationStatistics(body);
        }
    }

    private void validateReport(HashMap<String, Object> map) {
        ApiParam apiParam = ApiParam.builder().query(String.valueOf(map.get(JobConfig.KEY_CUSTOM_REPORT_SQL)))
                .option(QueryOptionEnum.SYNTAX_VALIDATION.getValue())
                .build();
        BaseResult baseResult = apiService.executeQuery(apiParam);
        if (!baseResult.isSuccess()) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                    "Job Validation Error: ".concat(baseResult.getMessage()), null);
        }
    }


    private void syntaxValidationFieldDiscovery(HashMap<String, Object> map) {
        String syntaxValidation = env.getProperty("SYNTAX_VALIDATION");
        List field = (List) map.get("custom.field_discovery.fields");
        Object filter = map.get("custom.field_discovery.filter");
        String validationSql = String.format(syntaxValidation, String.join(",", field), map.get("query.data_source"), StringUtil.isEmpty(filter) ? " 1 = 1" : filter);
        ApiParam apiParam = ApiParam.builder().query(validationSql).option(QueryOptionEnum.SYNTAX_VALIDATION.getValue()).build();
        BaseResult baseResult = apiService.executeQuery(apiParam);
        if (!baseResult.isSuccess()) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                    "Job Validation Error: ".concat(baseResult.getMessage()), null);
        }
    }

    private void syntaxValidationStatistics(HashMap<String, Object> body) {
        String sql = String.valueOf(body.get(JobConfig.KEY_CUSTOM_STATISTICS_SQL));

        BaseResult baseResult = apiService.executeQuery(ApiParam.builder().query(sql).option(QueryOptionEnum.SYNTAX_VALIDATION.getValue()).build());
        if (!baseResult.isSuccess()) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                    "Job Validation Error: ".concat(baseResult.getMessage()), null);
        }
    }

    public static boolean executeUpdate(String sql) {
        Connection conn = null;
        java.sql.Statement statement = null;
        int i;
        try {
            log.info("{} engine execute update: {}", DBTypeEnum.HBASE.getValue(), sql);
            PROPERTIES.setProperty("phoenix.query.timeoutMs", hBaseAPISource.getRpcTimeout());
            String url = URL_PREFIX.concat(hBaseAPISource.getZookeeperQuorum()).concat(":").concat(hBaseAPISource.getZookeeperZnodeParent());
            conn = DriverManager.getConnection(url, PROPERTIES);
            statement = conn.createStatement();
            i = statement.executeUpdate(sql);
            conn.commit();
        } catch (RuntimeException | SQLException e) {
            log.error("{} engine error: {}", DBTypeEnum.HBASE.getValue(), e);
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.SQL_SYNTAX_ERROR.getCode(), e.getMessage(), null);
        } finally {
            close(statement, conn);
        }
        return i == 1;
    }

    private List buildData(String id) {
        Map<String, Object> data = Maps.newHashMap();
        data.put("jobId", id);
        return Lists.newArrayList(data);
    }

    public static void execute(String sql) {
        Connection conn = null;
        java.sql.Statement statement = null;
        try {
            log.info("DB Engine is :{}, Execute is: {}", DBTypeEnum.HBASE.getValue(), sql);
            PROPERTIES.setProperty("phoenix.query.timeoutMs", hBaseAPISource.getRpcTimeout());
            String url = URL_PREFIX.concat(hBaseAPISource.getZookeeperQuorum()).concat(":").concat(hBaseAPISource.getZookeeperZnodeParent());
            conn = DriverManager.getConnection(url, PROPERTIES);
            statement = conn.createStatement();
            statement.execute(sql);
            conn.commit();
        } catch (RuntimeException | SQLException e) {
            log.error("DB Engine is :{}, Query is error: {}", DBTypeEnum.HBASE.getValue(), e);
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.SQL_SYNTAX_ERROR.getCode(), e.getMessage(), null);
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
}
