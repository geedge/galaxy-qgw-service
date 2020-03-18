package com.mesalab.qgw.controller;

import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.BaseResultEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.qgw.service.SystemService;
import com.zdjizhi.utils.DateUtils;
import com.zdjizhi.utils.StringUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
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

    @ApiOperation(value = "数据删除", httpMethod = "POST", notes = "修改日志删除策略")
    @PostMapping(value = "storage/deletion")
    public BaseResult storageDeletion(@RequestBody Map<String, String> logInfo) {
        log.debug("数据删除接口");
        String logType = logInfo.get("logType");
        String maxDays = logInfo.get("maxDays");
        if (StringUtil.isBlank(logType) || StringUtil.isBlank(maxDays)) {
            throw new BusinessException(BaseResultEnum.FAIL.getCode(), "参数校验: logType 与 maxDays 需同时填写", null);
        }
        if (!StringUtil.isNumeric(maxDays)) {
            throw new BusinessException(BaseResultEnum.FAIL.getCode(), "参数校验: maxDays 必须是数值", null);
        }
        if ("ALL".equals(logType.toUpperCase()) && Integer.parseInt(maxDays) != 0) {
            throw new BusinessException(BaseResultEnum.FAIL.getCode(), "参数校验: logType为All：maxDays 必须为0", null);
        }
        if (!"ALL".equals(logType.toUpperCase()) && !(Integer.parseInt(maxDays) >= 1 && Integer.parseInt(maxDays) <= 2000)) {
            throw new BusinessException(BaseResultEnum.FAIL.getCode(), "参数校验: logType非All: maxDays 必须是数值且[1-2000]", null);
        }
        return systemService.deleteStorage(logInfo.get("logType").trim(), Integer.parseInt(maxDays));
    }
}
