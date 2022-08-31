package com.mesalab.qgw.controller;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.qgw.model.job.EncryptionInfo;
import com.mesalab.qgw.model.job.StorageDeletionInfo;
import com.mesalab.qgw.service.SystemService;
import com.mesalab.services.configuration.ThreadPoolMonitor;
import com.zdjizhi.utils.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/sys")
public class SystemController {
    private static final Log log = LogFactory.get();
    @Autowired
    private SystemService systemService;
    @Autowired
    ThreadPoolMonitor threadPoolMonitor;


    @RequestMapping("/storage/quota")
    public BaseResult storageQuota() {
        return systemService.getStorageQuota();
    }

    @RequestMapping("storage/daily-trend")
    public BaseResult dailyTrendOfStorage(@RequestParam Map<String, Object> param) {
        String searchStartTime = StringUtil.stripToEmpty((String) param.get("start_time"));
        String searchEndTime = StringUtil.stripToEmpty((String) param.get("end_time"));
        return systemService.dailyTrendOfStorage(searchStartTime, searchEndTime);
    }

    @PostMapping(value = "storage/deletion")
    public BaseResult storageDeletion(@RequestBody List<StorageDeletionInfo> list) {
        log.warn("数据配额设置, 参数: params is {}", list);
        if (StringUtil.isEmpty(list)) {
            return BaseResultGenerator.failure(ResultStatusEnum.FAIL.getCode(), "parameter not obtained");
        }
        for (StorageDeletionInfo info : list) {
            if (StringUtil.isBlank(info.getLogType()) || StringUtil.isBlank(String.valueOf(info.getMaxDays()))) {
                throw new BusinessException(ResultStatusEnum.FAIL.getCode(), "Parameter verification: logType and maxDays need to be filled in at the same time", null);
            }
            if ("ALL".equalsIgnoreCase(info.getLogType()) && list.size() != 1) {
                throw new BusinessException(ResultStatusEnum.FAIL.getCode(), "Parameter verification: When logType exists in ALL, it cannot coexist with other types", null);
            }
            if (!(info.getMaxDays() >= 0 && info.getMaxDays() <= 2000)) {
                throw new BusinessException(ResultStatusEnum.FAIL.getCode(), "Parameter check: logType is not All: maxDays must be numeric and [0-2000]", null);
            }
        }
        return systemService.deleteStorage(list);
    }

    @GetMapping(value = "storage/deletion")
    public BaseResult storageDeletionStatus(StorageDeletionInfo storageDeletionInfo) {

        log.warn("数据配额设置状态, 参数: params is {}", storageDeletionInfo);
        return systemService.getDeleteStorageStatus(storageDeletionInfo.getLogType());
    }

    @DeleteMapping(value = "engine/tasks/{queryId}")
    public BaseResult killQuery(@PathVariable String queryId) {
        log.warn("停止查询, 参数: queryId is {}", queryId);
        if (StringUtil.isBlank(queryId)) {
            return BaseResultGenerator.success4Message("ok");
        }
        return systemService.deleteQueryTask(queryId);
    }

    @GetMapping(value = "engine/processes/{queryId}")
    public BaseResult getProcessesByQueryId(@PathVariable String queryId) {
        log.info("查询SQL任务执行状态, 参数: queryId is {}", queryId);
        return systemService.getProcessesByQueryId(queryId);
    }

    @RequestMapping(value = "engine/queryIds")
    public BaseResult getQueryId(@RequestParam Map<String, Object> param) {
        log.info("获取当前SQL Query ID, 参数: {}", param);
        Object resultId, query;
        if (StringUtil.isEmpty(resultId = param.get("resultId"))
                || StringUtil.isEmpty(query = param.get("query"))) {

            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.ILLEGAL_ARGUMENT_EXCEPTION.getCode(),
                    "Param resultId or query cannot be empty", null);
        }
        String queryId = systemService.getCustomQueryId(resultId.toString(), query.toString());

        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Object> map = new HashMap<>(16);
        map.put("queryId", queryId);
        result.add(map);
        return BaseResultGenerator.success(result);
    }
    @GetMapping(value = "encryption")
    public BaseResult getCiphertext(EncryptionInfo param) {
        log.info("Plaintext encrypted,The plaintext argument is : {}", param);
        if (StringUtil.isEmpty(param.getPassword()) || StringUtil.isEmpty(param.getSalt())) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.ILLEGAL_ARGUMENT_EXCEPTION.getCode(),
                    "Param password or salt cannot be empty", null);
        }
        return systemService.getCiphertext(param);
    }

    @GetMapping(value = "/clear/realJobQueue")
    public BaseResult clearTaskQueue() {
        log.info("Clear real job queue.");
        threadPoolMonitor.clearQueue();
        return BaseResultGenerator.success();
    }

    @GetMapping(value = "/storage/consistency")
    public BaseResult monitorCluster() {
        log.info("consistency check interface.");
        return systemService.consistencyCheck();
    }
}
