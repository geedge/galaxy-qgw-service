package com.mesalab.qgw.model.basic;

import com.google.common.collect.Maps;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.services.configuration.JobConfig;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * TODO
 *
 * @Classname QueryJobInfo
 * @Date 2023/12/6 18:20
 * @Author wWei
 */
@Data
public class QueryCache implements Serializable {
    private String type;
    private BaseResult<Object> baseResult;

    public QueryCache() {
        this.baseResult = new BaseResult<>();
        Map<String, Object> job = Maps.newHashMap();
        job.put("job_proper", Maps.newLinkedHashMap());
        job.put(JobConfig.IS_DONE, false);
        job.put(JobConfig.DONE_PROGRESS, 0.0);
        job.put(JobConfig.IS_CANCELED, false);
        job.put(JobConfig.IS_FAILED, false);
        job.put(JobConfig.LAST_QUERY_TIME, System.currentTimeMillis() / 1000);
        this.baseResult.setJob(job);
    }

    /*@Data
    private static class Job {
        private Long lastQueryTime;
        private Long startTime;
        private Long endTime;
    }*/
    /*private Long count;

    private String outputMode;

    private Links links;
    private Map<String, Object> customAttributes = Maps.newHashMap();

    */
}
