package com.measlab.tsg.controller;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.measlab.tsg.service.JobService;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.qgw.config.JobConfig;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.qgw.service.DeferredResultHolder;
import com.mesalab.qgw.service.MetadataService;
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
    MetadataService metadataService;

    @Autowired
    private DeferredResultHolder deferredResultHolder;

    @PostMapping(value = "/jobs", produces = "application/json")
    public BaseResult commitJob(@RequestBody HashMap<String, Object> reqBody) {
        validateJobParams(reqBody);
        BaseResult baseResult;
        String type = String.valueOf(reqBody.get(JobConfig.KEY_QUERY_TYPE));
        if (JobConfig.FIELD_DISCOVERY.equals(type) || JobConfig.STATISTICS.equals(type)) {
            baseResult = jobService.commitAdHocQuery(reqBody);
        } else if (JobConfig.REPORT.equals(type)) {
            baseResult = jobService.commitSavedQuery(reqBody);
        } else {
            baseResult = BaseResultGenerator.failure(ResultStatusEnum.NOT_FOUND.getCode(), "Not Support");
        }
        return baseResult;
    }

    @GetMapping(value = "/jobs/{jobId}/field_discovery")
    public DeferredResult<BaseResult> longPollingFieldDiscoveryResult(@PathVariable String jobId) {
        log.info("Long Polling Request,Job ID is {}", jobId);
        BaseResult baseResult = null;
        baseResult = jobService.getResultFieldDiscovery(jobId);
        DeferredResult<BaseResult> deferredResult =
                deferredResultHolder.newDeferredResult(jobId, jobConfig.getResponseTimeout(), baseResult);
        if (!jobConfig.isLongPollingEnabled()) {
            deferredResultHolder.handleDeferredData(deferredResult, baseResult);
            return deferredResult;
        }
        deferredResult.onTimeout(() -> {
            deferredResultHolder.handleDeferredData(deferredResult, jobService.getResultFieldDiscovery(jobId));
        });

        if (StringUtil.isNotEmpty(baseResult)
                && StringUtil.isNotEmpty(baseResult.getJob())
                && Boolean.valueOf(String.valueOf(baseResult.getJob().get(JobConfig.IS_DONE)))) {
            deferredResultHolder.handleDeferredData(deferredResult, baseResult);
        }
        return deferredResult;
    }

    @GetMapping(value = "/jobs/{jobId}/statistics")
    public DeferredResult<BaseResult> longPollingStatisticsResult(@PathVariable String jobId) {
        log.info("Long Polling Request,Job ID is {}", jobId);
        BaseResult baseResult = null;
        baseResult = jobService.getResultStatistics(jobId);
        DeferredResult<BaseResult> deferredResult =
                deferredResultHolder.newDeferredResult(jobId, jobConfig.getResponseTimeout(), baseResult);
        if (!jobConfig.isLongPollingEnabled()) {
            deferredResultHolder.handleDeferredData(deferredResult, baseResult);
            return deferredResult;
        }
        deferredResult.onTimeout(() -> {
            deferredResultHolder.handleDeferredData(deferredResult, jobService.getResultStatistics(jobId));
        });

        if (StringUtil.isNotEmpty(baseResult)
                && StringUtil.isNotEmpty(baseResult.getJob())
                && Boolean.valueOf(String.valueOf(baseResult.getJob().get(JobConfig.IS_DONE)))) {
            deferredResultHolder.handleDeferredData(deferredResult, baseResult);
        }
        return deferredResult;
    }

    @GetMapping(value = "/jobs/{jobId}/report")
    public BaseResult getResultReport(@PathVariable String jobId) {
        return jobService.getResultReport(jobId);
    }

    @GetMapping(value = "/jobs/{jobId}/status")
    public BaseResult getStatus(@PathVariable String jobId) {
        if (jobId.startsWith(DigestUtil.md5Hex16(JobConfig.REPORT).concat(JobConfig.DELIMIT))) {
            return jobService.getReportStatus(jobId);
        }
        return BaseResultGenerator.success();
    }

    @DeleteMapping(value = "/jobs/{jobId}")
    public BaseResult cancelledJob(@PathVariable String jobId) {
        if (jobId.startsWith(DigestUtil.md5Hex16(JobConfig.REPORT).concat(JobConfig.DELIMIT))) {
            return jobService.cancelledReportJob(jobId);
        }
        return BaseResultGenerator.success();
    }



    private void validateJobParams(HashMap<String, Object> reqBody) {
        if (!reqBody.containsKey(JobConfig.KEY_QUERY_TYPE)) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), QGWMessageConst.QUERY_TYPE_NOT_NULL));
        }
        if (!reqBody.containsKey(JobConfig.KEY_QUERY_DATA_SOURCE)) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),QGWMessageConst.QUERY_DATA_SOURCE_NOT_NULL));
        }
        String dataSource = String.valueOf(reqBody.get(JobConfig.KEY_QUERY_DATA_SOURCE));
        if (!metadataService.getAllTable().contains(dataSource)) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),QGWMessageConst.QUERY_DATA_SOURCE_ERROR));
        }
    }

}
