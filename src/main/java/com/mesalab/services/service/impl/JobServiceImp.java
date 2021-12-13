package com.mesalab.services.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
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
        syntaxValidation(map);
        String id = DigestUtil.md5Hex(String.valueOf(map));
        jobExecuteService.addExecutor(id, map);
        return BaseResultGenerator.success("ok", build(id));
    }

    @Override
    public BaseResult getResult(String id) {
        updateQueryTime(id, System.currentTimeMillis());
        return buildResult(id);
    }

    private BaseResult<List<Map<String, Object>>> buildResult(String id) {
        BaseResult baseResult = apiService.executeQuery(
                ApiParam.builder().
                        option("real-time").
                        query(String.format(Objects.requireNonNull(env.getProperty("JOB_RESULT")), id)).
                        build());
        if (!baseResult.isSuccess()) {
            throw new BusinessException(ResultStatusEnum.SERVER_ERROR.getCode(), ResultCodeEnum.UNKNOW_ERROR.getCode(),
                    "get Job Result Error: ".concat(baseResult.getMessage()), null);
        }
        List<Map<String, Object>> data = (List<Map<String, Object>>) baseResult.getData();
        if (StringUtil.isEmpty(data)) {
            throw new BusinessException(ResultStatusEnum.NOT_FOUND.getCode(), ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                    "Not Found This Job: ".concat(id), null);
        }
        Map<String, Object> job = Maps.newHashMap();
        Map<String, Object> map = data.get(0);

        String[] list = String.valueOf(map.get("discovery_field")).split(",");
        List<Map<String, Object>> res = Lists.newArrayList();
        for (String column : list) {
            Map<String, Object> result = Maps.newHashMap();
            Object o = map.get(column);
            if (StringUtil.isNotEmpty(o)) {
                result.put(column, JsonMapper.fromJsonString(String.valueOf(o), Map.class));
                res.add(result);
            }
        }
        job.put("is_done", map.get("is_done"));
        job.put("done_progress", map.get("done_progress"));
        job.put("is_canceled", map.get("is_canceled"));
        return BaseResultGenerator.success(job, res);
    }

    private void updateQueryTime(String id, long now) {
        executeUpdate(String.format(Objects.requireNonNull(env.getProperty("JOB_RESULT_QUERY")), id, now));
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
            log.info("DB Engine is :{}, Execute Query is: {}", DBTypeEnum.HBASE.getValue(), sql);
            PROPERTIES.setProperty("phoenix.query.timeoutMs", hBaseAPISource.getRpcTimeout());
            String url = URL_PREFIX.concat(hBaseAPISource.getZookeeperQuorum()).concat(":").concat(hBaseAPISource.getZookeeperZnodeParent());
            conn = DriverManager.getConnection(url, PROPERTIES);
            statement = conn.createStatement();
            i = statement.executeUpdate(sql);
            conn.commit();
        } catch (RuntimeException | SQLException e) {
            log.error("DB Engine is :{}, Query is error: {}", DBTypeEnum.HBASE.getValue(), e);
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
