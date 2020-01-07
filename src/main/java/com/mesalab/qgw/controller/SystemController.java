package com.mesalab.qgw.controller;

import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.qgw.service.SystemService;
import com.zdjizhi.utils.DateUtils;
import com.zdjizhi.utils.StringUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
