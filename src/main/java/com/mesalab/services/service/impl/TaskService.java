package com.mesalab.services.service.impl;


import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.collect.Lists;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.enums.QueryOptionEnum;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.service.ApiService;
import com.mesalab.qgw.service.MetadataService;
import com.mesalab.services.common.property.SqlPropertySourceFactory;
import com.mesalab.services.configuration.TaskCfg;
import com.mesalab.services.service.JobService;
import com.zdjizhi.utils.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;

import static com.mesalab.services.service.impl.JobServiceImp.execute;
import static com.mesalab.services.service.impl.JobServiceImp.executeUpdate;

/**
 * TODO
 *
 * @Classname TaskService
 * @Date 2021/11/25 10:11 下午
 * @Author wWei
 */
@Service("taskService")
@PropertySource(value = "classpath:job-sql-template.sql", factory = SqlPropertySourceFactory.class)
public class TaskService implements EnvironmentAware {
    private static final Log log = LogFactory.get();
    final Object lock = new Object();
    @Autowired
    Environment env;
    @Autowired
    ApiService apiService;
    @Autowired
    JobService jobService;
    @Autowired
    SubTaskService subTaskService;
    @Autowired
    MetadataService metadataService;
    @Autowired
    TaskCfg taskCfg;

    @Async("taskExecutor")
    public void addExecutor(String id, HashMap<String, Object> param) {
        long now = System.currentTimeMillis();
        if (isExist(id)) {
            return;
        }
        List<String> fields = (List<String>) param.get("custom.field_discovery.fields");
        init(id, fields);
        String logType = (String) param.get("query.data_source");
        String filter = (String) param.get("custom.field_discovery.filter");
        executeSubTask(id, now, fields, logType, filter);
    }

    private void executeSubTask(String id, long now, List<String> fields, String logType, String filter) {
        long count = getCount(logType, filter);
        if (isCancel(id, now)) {
            return;
        }
        for (String field : fields) {
            subTaskService.execute(id, logType, filter, field, count);
        }
        try {
            updateProgress(id, fields.size());
        } catch (Exception e) {
            log.error("Update Progress Error", e);
        }
    }

    private void updateProgress(String id, int allColumnNum) throws Exception {
        int num = 0;
        while (Thread.currentThread().isAlive()) {
            if (num++ > 50) {
                break;
            }
            synchronized (lock) {
                lock.wait(taskCfg.getProgressTTL());
            }
            BaseResult result = jobService.getResult(id);
            if (result.isSuccess() && StringUtil.isNotEmpty(result.getData())) {
                Object data = result.getData();
                List<Map<String, Object>> list = (List<Map<String, Object>>) data;
                execute(String.format(Objects.requireNonNull(env.getProperty("JOB_UPSET_PROGRESS")),
                        id, list.size() == allColumnNum, Double.parseDouble(String.format("%.4f", (list.size() * 1.0 / allColumnNum)))));
                if (list.size() >= allColumnNum) {
                    break;
                }
            }
        }
    }

    private boolean isCancel(String id, long now) {
        ApiParam build = ApiParam.builder().query(String.format(Objects.requireNonNull(env.getProperty("JOB_TTL")), id)).
                option(QueryOptionEnum.REAL_TIME.getValue()).build();
        BaseResult baseResult = apiService.executeQuery(build);
        if (baseResult.isSuccess() && (StringUtil.isNotEmpty(baseResult.getData()))) {
            List<Map<String, Object>> list = (List<Map<String, Object>>) baseResult.getData();
            long statTime = Long.parseLong(list.get(0).get("stat_time").toString());
            if ((now - statTime) > taskCfg.getRunningTTL()) {
                executeUpdate(String.format(Objects.requireNonNull(env.getProperty("JOB_SET_CANCELED")), id, true));
                return true;
            }
        }
        return false;
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
                    id, false, 0, false, System.currentTimeMillis(), String.join(",", fields)));
        }
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
        return !(Boolean) map.get("isCanceled");
    }

    private Long getCount(String logType, String filter) {
        String sql = String.format(Objects.requireNonNull(env.getProperty("JOB_LOG_COUNT")),
                logType, StrUtil.isBlankIfStr(filter) ? "" : "WHERE ".concat(filter));
        BaseResult baseResult = apiService.executeQuery(ApiParam.builder().query(sql).build());
        if (baseResult.isSuccess()) {
            List<Map<String, Object>> data = (List<Map<String, Object>>) baseResult.getData();
            Object count = data.get(0).get("count");
            return Long.parseLong(count.toString());
        }
        return 0L;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.env = environment;
    }
}
