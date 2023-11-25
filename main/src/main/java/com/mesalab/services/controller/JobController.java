package com.mesalab.services.controller;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.qgw.service.DeferredResultHolder;
import com.mesalab.qgw.service.MetadataService;
import com.mesalab.services.configuration.JobConfig;
import com.mesalab.services.service.JobService;
import com.geedgenetworks.utils.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.HashMap;

import static com.mesalab.services.configuration.JobConfig.*;

/**
 * TODO
 *
 * @Classname jobController
 * @Date 2021/11/24 3:54 下午
 * @Author wWei
 */
@RestController
@RequestMapping(value = "/query")
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
    public BaseResult commit(@RequestBody HashMap<String, Object> reqBody) {
        log.info("Commit Job, param is {}", reqBody);
        validateJobParams(reqBody);
        String type = String.valueOf(reqBody.get(JobConfig.KEY_QUERY_TYPE));
        if (JobConfig.FIELD_DISCOVERY.equals(type) || JobConfig.LONG_TERM.equals(type)) {
            return jobService.commitAdHocQuery(reqBody);
        } else if (JobConfig.SAVED_QUERY.equals(type)) {
            return jobService.commitSavedQuery(reqBody);
        }
        throw new BusinessException(ResultStatusEnum.NOT_FOUND.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), "Not Support"));
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

    @GetMapping(value = {"/jobs/{jobId}/long_term", "/jobs/{jobId}/statistics"})
    public DeferredResult<BaseResult> longPollingLongTermResult(@PathVariable String jobId) {
        log.info("Long Polling Request,Job ID is {}", jobId);
        BaseResult baseResult = null;
        baseResult = jobService.longTermResult(jobId);
        DeferredResult<BaseResult> deferredResult =
                deferredResultHolder.newDeferredResult(jobId, jobConfig.getResponseTimeout(), baseResult);
        if (!jobConfig.isLongPollingEnabled()) {
            deferredResultHolder.handleDeferredData(deferredResult, baseResult);
            return deferredResult;
        }
        deferredResult.onTimeout(() -> {
            deferredResultHolder.handleDeferredData(deferredResult, jobService.longTermResult(jobId));
        });

        if (StringUtil.isNotEmpty(baseResult)
                && StringUtil.isNotEmpty(baseResult.getJob())
                && Boolean.valueOf(String.valueOf(baseResult.getJob().get(JobConfig.IS_DONE)))) {
            deferredResultHolder.handleDeferredData(deferredResult, baseResult);
        }
        return deferredResult;
    }

    @GetMapping(value = "/jobs/{jobId}/saved_query")
    public BaseResult savedQueryResult(@PathVariable String jobId) {
        return jobService.getSavedQueryResult(jobId);
    }

    @GetMapping(value = "/jobs/{jobId}/status")
    public BaseResult getStatus(@PathVariable String jobId) {
        if (jobId.startsWith(DigestUtil.md5Hex16(SAVED_QUERY).concat(DELIMIT))) {
            return jobService.getSavedQueryStatus(jobId);
        }
        return BaseResultGenerator.success();
    }

    @DeleteMapping(value = "/jobs/{jobId}")
    public BaseResult cancel(@PathVariable String jobId) {
        if (jobId.startsWith(DigestUtil.md5Hex16(SAVED_QUERY).concat(DELIMIT))) {
            return jobService.cancelSavedQuery(jobId);
        }
        return BaseResultGenerator.success();
    }

    private void validateJobParams(HashMap<String, Object> reqBody) {
        if (!reqBody.containsKey(JobConfig.KEY_QUERY_TYPE)) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), QGWMessageConst.QUERY_TYPE_NOT_NULL));
        }
        if (JobConfig.STATISTICS_DEPRECATED.equals(String.valueOf(reqBody.get(KEY_QUERY_TYPE)))) {
            reqBody.put(KEY_QUERY_TYPE, LONG_TERM);
            reqBody.put(KEY_CUSTOM_LONG_TERM_SQL, String.valueOf(reqBody.get(KEY_CUSTOM_STATISTICS_SQL_DEPRECATED)));
            reqBody.remove(JobConfig.STATISTICS_DEPRECATED);
            reqBody.remove(JobConfig.KEY_CUSTOM_STATISTICS_SQL_DEPRECATED);
        }
        if (!reqBody.containsKey(JobConfig.KEY_QUERY_DATA_SOURCE)) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), QGWMessageConst.QUERY_DATA_SOURCE_NOT_NULL));
        }
        String dataSource = String.valueOf(reqBody.get(JobConfig.KEY_QUERY_DATA_SOURCE));
        if (!metadataService.getAllTable().contains(dataSource)) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), QGWMessageConst.QUERY_DATA_SOURCE_ERROR));
        }
    }
}
