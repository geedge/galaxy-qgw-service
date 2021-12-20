package com.mesalab.services.service.impl;


import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.enums.QueryOptionEnum;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.service.ApiService;
import com.mesalab.qgw.service.MetadataService;
import com.mesalab.services.common.property.SqlPropertySourceFactory;
import com.mesalab.services.configuration.JobConfig;
import com.mesalab.services.service.JobService;
import com.zdjizhi.utils.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.mesalab.services.service.impl.JobServiceImp.execute;
import static com.mesalab.services.service.impl.JobServiceImp.executeUpdate;

/**
 * TODO
 *
 * @Classname TaskService
 * @Date 2021/11/25 10:11 下午
 * @Author wWei
 */
@Service("jobExecuteService")
@PropertySource(value = "classpath:job-sql-template.sql", factory = SqlPropertySourceFactory.class)
public class JobExecuteService implements EnvironmentAware {
    private static final Log log = LogFactory.get();
    @Autowired
    Environment env;
    @Autowired
    ApiService apiService;
    @Autowired
    JobService jobService;
    @Autowired
    TaskExecuteService taskExecuteService;
    @Autowired
    MetadataService metadataService;
    @Autowired
    JobConfig jobCfg;

    @Async("jobExecutor")
    public void addExecutor(String id, HashMap<String, Object> param) {
        Stopwatch watch = Stopwatch.createStarted();
        try {
            List<String> fields = (List<String>) param.get("custom.field_discovery.fields");
            String logType = (String) param.get("query.data_source");
            String filter = (String) param.get("custom.field_discovery.filter");
            executeTask(id, watch, fields, logType, filter);
        } catch (Exception e) {
            log.error(e, "execute job error: jobId-{}", id);
        } finally {
            execute(String.format(Objects.requireNonNull(env.getProperty("JOB_UPSET_PROGRESS")),
                    id, true, 1, watch.elapsed(TimeUnit.MILLISECONDS)));
        }
    }

    private void executeTask(String id, Stopwatch watch, List<String> fields, String logType, String filter) {
        long count = getCount(logType, filter);
        if (count == 0 || isCancel(id)) {
            return;
        }
        executeUpdate(String.format(Objects.requireNonNull(env.getProperty("JOB_UPSERT_DETAIL")),
                "count", id, count));
        List<Future<Boolean>> list = Lists.newArrayList();
        for (String field : fields) {
            list.add(taskExecuteService.execute(id, logType, filter, field, count));
        }
        updateProcess(id, watch, fields, list);
    }

    private void updateProcess(String id,Stopwatch watch, List<String> fields, List<Future<Boolean>> list) {
        double process = 0.0;
        while (true) {
            if (watch.elapsed(TimeUnit.MILLISECONDS) > jobCfg.getExecutionTimeout()) {
                list.forEach(o -> o.cancel(true));
                execute(String.format(Objects.requireNonNull(env.getProperty("JOB_UPSET_PROGRESS")),
                        id, true, 1, watch.elapsed(TimeUnit.MILLISECONDS)));
                break;
            }

            int doneNum = list.parallelStream().filter(Future::isDone).toArray().length;
            if (doneNum >= fields.size()) {
                break;
            }
            DecimalFormat format = new DecimalFormat("#0.#");
            format.applyPattern("0.0");
            format.setRoundingMode(RoundingMode.FLOOR);
            double v = Double.parseDouble(format.format(doneNum * 1.0 / fields.size()));
            if (v - process >= 0.1) {
                process = v;
                execute(String.format(Objects.requireNonNull(env.getProperty("JOB_UPSET_PROGRESS")),
                        id, doneNum == fields.size(), Double.parseDouble(String.format("%.4f", (doneNum * 1.0 / fields.size()))), watch.elapsed(TimeUnit.MILLISECONDS)));
            }
        }
    }

    private boolean isCancel(String id) {
        ApiParam build = ApiParam.builder().query(String.format(Objects.requireNonNull(env.getProperty("JOB_TTL")), id)).
                option(QueryOptionEnum.REAL_TIME.getValue()).build();
        BaseResult baseResult = apiService.executeQuery(build);
        if (baseResult.isSuccess() && (StringUtil.isNotEmpty(baseResult.getData()))) {
            List<Map<String, Object>> list = (List<Map<String, Object>>) baseResult.getData();
            long statTime = Long.parseLong(list.get(0).get("stat_time").toString());
            if ((System.currentTimeMillis() - statTime) > jobCfg.getInteractiveTimeout()) {
                executeUpdate(String.format(Objects.requireNonNull(env.getProperty("JOB_UPSERT_DETAIL")), "is_canceled", id, true));
                return true;
            }
        }
        return false;
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
