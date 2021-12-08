package com.mesalab.services.controller;

import com.mesalab.common.base.BaseResult;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.services.service.JobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
    @Autowired
    JobService jobService;

    @PostMapping(value = "/jobs", produces = "application/json")
    public BaseResult getJobId(@RequestBody HashMap<String, Object> reqBody) {
        validate(reqBody);
        return jobService.getJobId(reqBody);
    }

    @GetMapping(value = "/jobs/{jobId}/field_discovery")
    public BaseResult getJobResult(@PathVariable String jobId) {
        return jobService.getResult(jobId);
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
