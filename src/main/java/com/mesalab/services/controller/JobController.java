package com.mesalab.services.controller;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.mesalab.common.base.BaseResult;
import com.mesalab.qgw.service.DeferredResultHolder;
import com.mesalab.services.configuration.JobConfig;
import com.mesalab.services.service.JobService;
import com.zdjizhi.utils.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.HashMap;

/**
 * TODO
 *
 * @Classname jobController
 * @Date 2021/11/24 3:54 下午
 * @Author wWei
 */
@RestController
@RequestMapping(value = "/admin/query/")
public class JobController {
    private static final Log log = LogFactory.get();

    @Autowired
    JobService jobService;
    @Autowired
    JobConfig jobConfig;

    @Autowired
    private DeferredResultHolder deferredResultHolder;

    @PostMapping(value = "/jobs", produces = "application/json")
    public BaseResult getJobId(@RequestBody HashMap<String, Object> reqBody) {
        return jobService.getJobId(reqBody);
    }

    @GetMapping(value = "/jobs/{jobId}/field_discovery/no_long_polling")
    public BaseResult getJobResult(@PathVariable String jobId) {
        return jobService.getResult(jobId);
    }


    @GetMapping(value = "/jobs/{jobId}/field_discovery")
    public DeferredResult<BaseResult> longPollingResult(@PathVariable String jobId) {
        log.info("Long Polling Request,Job ID is {}", jobId);
        BaseResult baseResult = jobService.getResult(jobId);
        DeferredResult<BaseResult> deferredResult =
                deferredResultHolder.newDeferredResult(jobId, jobConfig.getResponseTimeout(), baseResult);
        deferredResult.onTimeout(() ->{
            deferredResultHolder.handleDeferredData(deferredResult, jobService.getResult(jobId));
        });

        if (StringUtil.isNotEmpty(baseResult)
                && StringUtil.isNotEmpty(baseResult.getJob())
                && Boolean.valueOf(String.valueOf(baseResult.getJob().get(JobConfig.IS_DONE)))) {
            deferredResultHolder.handleDeferredData(deferredResult, baseResult);
        }
        return deferredResult;
    }



}
