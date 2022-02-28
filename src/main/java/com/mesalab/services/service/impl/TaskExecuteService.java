package com.mesalab.services.service.impl;


import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.collect.Maps;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.enums.QueryOptionEnum;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.service.ApiService;
import com.mesalab.services.common.property.SqlPropertySourceFactory;
import com.mesalab.services.configuration.JobConfig;
import com.zdjizhi.utils.JsonMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Future;

import static com.mesalab.services.service.impl.JobServiceImp.executeUpdate;

/**
 * TODO
 *
 * @Classname TaskService
 * @Date 2021/11/25 10:11 下午
 * @Author wWei
 */
@Service("taskExecuteService")
@PropertySource(value = "classpath:job-sql-template.sql", factory = SqlPropertySourceFactory.class)
public class TaskExecuteService implements EnvironmentAware {
    private static final Log log = LogFactory.get();
    @Autowired
    Environment env;
    @Autowired
    ApiService apiService;

    @Async("taskExecutor")
    public Future<Boolean> execute(String id, String logType, String filter, String field, long count) {
        String sql = String.format(Objects.requireNonNull(env.getProperty("JOB_TOPK")),
                field, logType, StrUtil.isBlankIfStr(filter) ? "" : "WHERE ".concat(filter));
        BaseResult baseResult = apiService.executeQuery(ApiParam.builder().option(QueryOptionEnum.REAL_TIME.getValue()).
                query(sql).build());
        if (!baseResult.isSuccess()) {
            executeUpdate(String.format(Objects.requireNonNull(env.getProperty("JOB_UPSERT_DETAIL")), JobConfig.IS_ERROR, id, true));
            log.error("subTask Error: ", baseResult.getMessage());
            return new AsyncResult<>(false);
    }
        List<Map<String, Object>> data = (List<Map<String, Object>>) baseResult.getData();
        Map<String, Object> map = Maps.newHashMap();
        map.put("topk", data.size() > 10 ? data.subList(0, 10) : data);
        map.put("distinct_count", data.size());
        map.put("count", count);
        String s = JsonMapper.toJsonString(map).replace("'", "\\'").replaceAll("\\\"", "\\\\\"");
        executeUpdate(String.format(Objects.requireNonNull(env.getProperty("JOB_UPSERT_RESULT")),
                field, id, s));
        return new AsyncResult<>(true);
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.env = environment;
    }
}
