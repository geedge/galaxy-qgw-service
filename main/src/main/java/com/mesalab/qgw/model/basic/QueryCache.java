package com.mesalab.qgw.model.basic;

import com.google.common.collect.Maps;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.services.configuration.JobConfig;
import lombok.Data;

import java.io.Serializable;
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
    private Long latestQueryTimeMs = System.currentTimeMillis();
    private BaseResult<Object> baseResult;
    public QueryCache(String jobId, String outputMode) {
        this.baseResult = new BaseResult<>();
        Map<String, Object> job = Maps.newLinkedHashMap();
        job.put(JobConfig.JOB_ID, jobId);
        job.put(JobConfig.IS_DONE, false);
        job.put(JobConfig.DONE_PROGRESS, 0.0);
        job.put(JobConfig.IS_CANCELED, false);
        job.put(JobConfig.IS_FAILED, false);
        job.put(JobConfig.START_TIME, null);
        job.put(JobConfig.END_TIME, null);
        job.put(JobConfig.OUTPUT_MODE, outputMode);
        Map<String, Object> links = Maps.newLinkedHashMap();
        links.put(JobConfig.LINKS_STATUS, "/v1/query/job/" + jobId);
        links.put(JobConfig.LINKS_RESULT, "/v1/query/job/" + jobId + "/result");
        job.put(JobConfig.LINKS, links);
        this.baseResult.setJob(job);
    }
}
