package com.mesalab.services.controller;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
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
    private DeferredResultHolder deferredResultHolder;

    @PostMapping(value = "/jobs", produces = "application/json")
    public BaseResult getJobId(@RequestBody HashMap<String, Object> reqBody) {
        validate(reqBody);
        return jobService.getJobId(reqBody);
    }

    @GetMapping(value = "/jobs/{jobId}/field_discovery/no_long_polling")
    public BaseResult getJobResult(@PathVariable String jobId) {
        return jobService.getResult(jobId);
    }

    @GetMapping(value = "/jobs/{jobId}/field_discovery")
    public DeferredResult<BaseResult> longPollingResult(@PathVariable String jobId) {
        log.info("Long Polling Request ， Job ID is {}", jobId);
        BaseResult baseResult = jobService.getResult(jobId);
        DeferredResult<BaseResult> deferredResult = deferredResultHolder.newDeferredResult(jobId, 3 * 1000l, baseResult);
        if (StringUtil.isNotEmpty(baseResult)
                && Boolean.valueOf(baseResult.getJob().get(JobConfig.IS_DONE).toString())) {
            deferredResultHolder.handleDeferredData(deferredResult, baseResult);
        }
        return deferredResult;
    }

    private void validate(HashMap<String, Object> body) {
        if (!body.containsKey("query.type")) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                    "Param Syntax Error: The param must contain query.type", null);
        }
        if (!body.containsKey("query.data_source")) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                    "Param Syntax Error: The param must contain query.data_source", null);
        }
        if (!body.containsKey("query.sample_ratio")) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                    "Param Syntax Error: The param must contain query.sample_ratio", null);
        }
    }

}
