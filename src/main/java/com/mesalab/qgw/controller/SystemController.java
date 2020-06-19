package com.mesalab.qgw.controller;

import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.qgw.model.job.StorageDeletionInfo;
import com.mesalab.qgw.service.SystemService;
import com.zdjizhi.utils.StringUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@Api(value = "/sys", tags = "系统相关接口")
@RestController
@RequestMapping(value = "/sys")
public class SystemController {

    @Autowired
    private SystemService systemService;


    @ApiOperation(value = "系统存储配额", httpMethod = "GET", notes = "获取数据平台存储数据配额(单位Byte)")
    @RequestMapping("/storage/quota")
    public BaseResult storageQuota() {
        log.debug("获取系统存储配额信息");
        return systemService.getStorageQuota();
    }

    @ApiOperation(value = "日志存储变化", httpMethod = "GET", notes = "获取数据平台存储变化(单位Byte)(默认最近一周)")
    @RequestMapping("storage/daily-trend")
    public BaseResult dailyTrendOfStorage(@RequestParam Map<String, Object> param) {
        log.debug("获取系统存储配额信息");



        String searchStartTime = StringUtil.stripToEmpty((String)param.get("start_time"));
        String searchEndTime =  StringUtil.stripToEmpty((String)param.get("end_time"));


        return systemService.dailyTrendOfStorage(searchStartTime, searchEndTime);
    }

    @ApiOperation(value = "数据配额设置", httpMethod = "POST", notes = "修改日志删除策略")
    @PostMapping(value = "storage/deletion")
    public BaseResult storageDeletion(@RequestBody List<StorageDeletionInfo> list) {
        log.info("数据配额设置, 参数: params is {}", list);

        if (StringUtil.isEmpty(list)) {
            return BaseResultGenerator.failure(ResultStatusEnum.FAIL.getCode(), "未获取到参数");
        }
        for (StorageDeletionInfo info : list) {
            if (StringUtil.isBlank(info.getLogType()) || StringUtil.isBlank(String.valueOf(info.getMaxDays()))) {
                throw new BusinessException(ResultStatusEnum.FAIL.getCode(), "参数校验: logType 与 maxDays 需同时填写", null);
            }
            if ("ALL".equalsIgnoreCase(info.getLogType()) && list.size() != 1) {
                throw new BusinessException(ResultStatusEnum.FAIL.getCode(), "参数校验: logType存在ALL时, 不能与其它类型共存", null);
            }
            if (!(info.getMaxDays() >= 0 && info.getMaxDays() <= 2000)) {
                throw new BusinessException(ResultStatusEnum.FAIL.getCode(), "参数校验: logType非All: maxDays 必须是数值且[0-2000]", null);
            }
        }
        return systemService.deleteStorage(list);
    }

    @ApiOperation(value = "数据配额设置状态", httpMethod = "GET", notes = "获取当前设置删除配额状态")
    @GetMapping(value = "storage/deletion")
    public BaseResult storageDeletionStatus(StorageDeletionInfo storageDeletionInfo) {

        log.info("数据配额设置状态, 参数: params is {}", storageDeletionInfo);
        return systemService.getDeleteStorageStatus(storageDeletionInfo.getLogType());
    }

    @ApiOperation(value = "终止查询任务", httpMethod = "DELETE", notes = "基于查询唯一标识查询任务")
    @DeleteMapping(value = "engine/tasks/{queryId}")
    public BaseResult killQuery(@PathVariable String queryId) {
        log.info("停止查询, 参数: queryId is {}", queryId);
        if (StringUtil.isBlank(queryId)) {
            return BaseResultGenerator.success4Message("ok");
        }
        return systemService.deleteQueryTask(queryId);
    }

}
