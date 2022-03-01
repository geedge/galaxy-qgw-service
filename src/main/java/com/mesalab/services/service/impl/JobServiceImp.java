package com.mesalab.services.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.DBTypeEnum;
import com.mesalab.common.enums.QueryOptionEnum;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.HBaseAPISource;
import com.mesalab.qgw.service.ApiService;
import com.mesalab.services.configuration.JobConfig;
import com.mesalab.services.service.JobService;
import com.mesalab.services.common.property.SqlPropertySourceFactory;
import com.zdjizhi.utils.JsonMapper;
import com.zdjizhi.utils.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.sql.*;
import java.util.*;

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
    public BaseResult getJobId(HashMap<String, Object> map) {
        validateRequestBody(map);
        syntaxValidation(map);
        String id = DigestUtil.md5Hex(String.valueOf(map));
        if (!isExist(id)) {
            log.info("Add Job, ID is {}, Params is {}", id, JsonMapper.toJsonString(map));
            init(id, (List<String>) map.get(JobConfig.CUSTOM_FIELD_DISCOVERY_FIELDS));
            jobExecuteService.addExecutor(id, map);
        }
        return BaseResultGenerator.success("ok", build(id));
    }

    @Override
    public BaseResult getResult(String id) {
        return buildResult(id);
    }

    private BaseResult<List<Map<String, Object>>> buildResult(String id) {
        List<Map<String, Object>> data = validationData(id);
        updateQueryTime(id, System.currentTimeMillis());
        Map<String, Object> job = Maps.newHashMap();
        Map<String, Object> map = data.get(0);
        String[] list = StringUtil.isEmpty(map.get(JobConfig.DISCOVERY_FIELD)) ? new String[0] : map.get(JobConfig.DISCOVERY_FIELD).toString().split(",");
        List<Map<String, Object>> resultList = Lists.newArrayList();
        for (String field : list) {
            Map<String, Object> result = Maps.newHashMap();
            Object item = map.get(field);
            if (StringUtil.isEmpty(item)) {
               continue;
            }
            try {
                Map fieldResult = JSONUtil.toBean(item.toString(), Map.class);
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
        return BaseResultGenerator.success(statistics, job, resultList);
    }

    private boolean isExist(String id) {
        String sql = String.format(Objects.requireNonNull(env.getProperty("JOB_STATUS")), id);
        BaseResult baseResult = apiService.executeQuery(ApiParam.builder().query(sql).build());
        if (!baseResult.isSuccess()) {
            log.error("Query Job Status Error", baseResult.getMessage());
        }
        List<Map<String, Object>> data = (List<Map<String, Object>>) baseResult.getData();
        if (StringUtil.isEmpty(data)) {
            return false;
        }
        Map<String, Object> map = data.get(0);
        if (StringUtil.isEmpty(map.get(JobConfig.IS_CANCELED))) {
            return false;
        }
        if ((Boolean) (map.get(JobConfig.IS_ERROR))
                && System.currentTimeMillis() - (Long) map.get("last_query_time") > jobCfg.getInteractiveTimeout()) {
            execute(String.format(Objects.requireNonNull(env.getProperty("JOB_DELETE")), id));
            return false;
        }
        return !(Boolean) map.get(JobConfig.IS_CANCELED);
    }

    private void init(String id, List<String> fields) {
        List<String> interim = Lists.newArrayList(fields);
        ApiParam apiParam = ApiParam.builder().query(env.getProperty("JOB_RESULT_ALL_COLUMN")).option(QueryOptionEnum.REAL_TIME.getValue()).build();
        BaseResult baseResult = apiService.executeQuery(apiParam);
        if (baseResult.isSuccess()) {
            List<Map<String, Object>> meta = (List<Map<String, Object>>) baseResult.getMeta();
            List<String> metaName = new ArrayList<>();
            meta.forEach(m -> metaName.add(m.get("name").toString()));
            interim.removeAll(metaName);
            for (String field : interim) {
                execute(String.format(Objects.requireNonNull(env.getProperty("JOB_ADD_COLUMN")), field));
            }
            execute(String.format(Objects.requireNonNull(env.getProperty("JOB_INIT")),
                    id, false, 0, false, false, System.currentTimeMillis(), String.join(",", fields), 0, 0));
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
        if (StringUtil.isEmpty(data) || StringUtil.isEmpty(data.get(0).get(JobConfig.DISCOVERY_FIELD))) {
            throw new BusinessException(ResultStatusEnum.NOT_FOUND.getCode(), ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                    "Not Found This Job: ".concat(id), null);
        }
        return data;
    }

    private void updateQueryTime(String id, long now) {
        executeUpdate(String.format(Objects.requireNonNull(env.getProperty("JOB_RESULT_QUERY")), id, now));
    }

    private void validateRequestBody(HashMap<String, Object> body) {
        if (!body.containsKey("query.type")) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                    "Param Syntax Error: The param must contain query.type", null);
        }
        if (!body.containsKey("query.data_source")) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                    "Param Syntax Error: The param must contain query.data_source", null);
        }
        if (!body.containsKey("query.sample_ratio")) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                    "Param Syntax Error: The param must contain query.sample_ratio", null);
        }
    }

    private void syntaxValidation(HashMap<String, Object> map) {
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

    private List build(String id) {
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
