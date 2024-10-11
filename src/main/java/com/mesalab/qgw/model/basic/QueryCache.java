package com.mesalab.qgw.model.basic;

import com.google.common.collect.Maps;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.services.configuration.JobConfig;
import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashMap;
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
    private Object requestParam;
    private Object jobContextParam;
    private Long latestQueryTimeMs = System.currentTimeMillis();
    private BaseResult<Object> baseResult;

    public QueryCache(String jobId, String type) {
        this.baseResult = new BaseResult<>();
        this.type = type;
        Map<String, Object> job = createJobMap(jobId);
        this.baseResult.setJob(job);
    }

    private Map<String, Object> createJobMap(String jobId) {
        Map<String, Object> job = Maps.newLinkedHashMap();
        job.put(JobConfig.JOB_ID, jobId);
        job.put(JobConfig.IS_DONE, false);
        job.put(JobConfig.DONE_PROGRESS, 0.0);
        job.put(JobConfig.IS_CANCELED, false);
        job.put(JobConfig.IS_FAILED, false);
        job.put(JobConfig.REASON, null);
        job.put(JobConfig.START_TIME, null);
        job.put(JobConfig.END_TIME, null);
        Map<String, Object> links = createLinksMap(jobId);
        job.put(JobConfig.LINKS, links);
        return job;
    }

    private Map<String, Object> createLinksMap(String jobId) {
        Map<String, Object> links = new LinkedHashMap<>();
        links.put(JobConfig.LINKS_STATUS, "/v1/query/job/" + jobId);
        if (JobConfig.LOG_QUERY.equals(type)) {
            links.put(JobConfig.LINKS_COUNT, "/v1/query/job/" + jobId + "/count");
            links.put(JobConfig.LINKS_LIST, "/v1/query/job/" + jobId + "/list");
            links.put(JobConfig.LINKS_TIMELINE, "/v1/query/job/" + jobId + "/timeline");
        } else {
            links.put(JobConfig.LINKS_RESULT, "/v1/query/job/" + jobId + "/result");
        }

        return links;
    }
}
