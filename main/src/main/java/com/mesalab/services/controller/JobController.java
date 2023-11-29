package com.mesalab.services.controller;

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
import java.util.List;
import java.util.Map;

import static com.mesalab.services.configuration.JobConfig.*;

/**
 * TODO
 *
 * @Classname jobController
 * @Date 2021/11/24 3:54 下午
 * @Author wWei
 */
@RestController
@RequestMapping(value = "/query/job")
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

    @PostMapping(consumes = "application/json")
    public BaseResult commit(@RequestBody HashMap<String, Object> reqBody) {
        log.info("Commit Job, param is {}", reqBody);
        validateJobParams(reqBody);
        String type = String.valueOf(reqBody.get(JobConfig.KEY_QUERY_TYPE));
        if (JobConfig.FIELD_DISCOVERY.equals(type) || JobConfig.STATISTICS.equals(type)) {
            return jobService.commitAdHocQuery(reqBody);
        } else if (JobConfig.SAVED_QUERY.equals(type)) {
            return jobService.commitSavedQuery(reqBody);
        }
        throw new BusinessException(ResultStatusEnum.NOT_FOUND.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), "Not Support"));
    }

    @GetMapping(value = "/{jobId}/field_discovery/result")
    public DeferredResult<BaseResult> fieldDiscoveryResult(@PathVariable String jobId) {
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

    @GetMapping(value = {"/{jobId}/statistics/result"})
    public DeferredResult<BaseResult> statisticsResult(@PathVariable String jobId) {
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

    @GetMapping(value = "/{jobId}/saved_query/result")
    public BaseResult savedQueryResult(@PathVariable String jobId) {
        return jobService.getSavedQueryResult(jobId);
    }

    //未实现
    @GetMapping(value = "/{jobId}/field_discovery")
    public BaseResult fieldDiscoveryStatus(@PathVariable String jobId) {
        throw new BusinessException(ResultStatusEnum.NOT_FOUND.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), "Not Support"));
    }

    //未实现
    @GetMapping(value = "/{jobId}/statistics")
    public BaseResult statisticsStatus(@PathVariable String jobId) {
        throw new BusinessException(ResultStatusEnum.NOT_FOUND.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), "Not Support"));
    }

    @GetMapping(value = "/{jobId}/saved_query")
    public BaseResult savedQueryStatus(@PathVariable String jobId) {
        return jobService.getSavedQueryStatus(jobId);
    }

    //未实现
    @DeleteMapping(value = "/{jobId}/field_discovery")
    public BaseResult cancelFieldDiscovery(@PathVariable String jobId) {
        throw new BusinessException(ResultStatusEnum.NOT_FOUND.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), "Not Support"));
    }

    //未实现
    @DeleteMapping(value = "/{jobId}/statistics")
    public BaseResult cancelStatistics(@PathVariable String jobId) {
        throw new BusinessException(ResultStatusEnum.NOT_FOUND.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), "Not Support"));
    }

    @DeleteMapping(value = "/{jobId}/saved_query")
    public BaseResult cancelSavedQuery(@PathVariable String jobId) {
        return jobService.cancelSavedQuery(jobId);
    }

    @GetMapping(consumes = "application/json")
    public BaseResult batchStatus(@RequestBody HashMap<String, Object> reqBody) {
        String queryType = String.valueOf(reqBody.get(JobConfig.KEY_QUERY_TYPE));
        List<String> ids = (List<String>) reqBody.get(JobConfig.KEY_QUERY_JOB_IDS);
        if (SAVED_QUERY.equals(queryType)) {
            List<Map<String, Object>> result = jobService.batchSavedQueryStatus(ids);
            return BaseResultGenerator.success(result);
        }
        throw new BusinessException(ResultStatusEnum.NOT_FOUND.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), "Not Support"));
    }

    @GetMapping(value = "/result", consumes = "application/json")
    public BaseResult batchResult(@RequestBody HashMap<String, Object> reqBody) {
        String queryType = String.valueOf(reqBody.get(JobConfig.KEY_QUERY_TYPE));
        List<String> ids = (List<String>) reqBody.get(JobConfig.KEY_QUERY_JOB_IDS);
        if (SAVED_QUERY.equals(queryType)) {
            List<Map<String, Object>> result = jobService.batchSavedQueryResult(ids);
            return BaseResultGenerator.success(result);
        }
        throw new BusinessException(ResultStatusEnum.NOT_FOUND.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), "Not Support"));
    }

    private void validateJobParams(HashMap<String, Object> reqBody) {
        if (!reqBody.containsKey(JobConfig.KEY_QUERY_TYPE)) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), QGWMessageConst.QUERY_TYPE_NOT_NULL));
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
