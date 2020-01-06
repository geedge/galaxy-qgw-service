package com.mesalab.qgw.controller;

import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.utils.DateUtils;
import com.mesalab.qgw.service.SystemService;
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
@Api(value = "/", tags = "系统相关接口")
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
        String start_time = param.get("start_time") == null? null: param.get("start_time").toString().replaceAll("\"|\'", "");
        String end_time = param.get("end_time") == null? null: param.get("end_time").toString().replaceAll("\"|\'", "");
        if(StringUtil.isEmpty(start_time) && StringUtil.isEmpty(end_time)){
            Date date = new Date();
            start_time = DateUtils.getRangeNdayOfTime(DateUtils.formatDateTime(date), -7);
            end_time = DateUtils.formatDateTime(date);
        }else if(StringUtil.isEmpty(start_time)){
            start_time = DateUtils.getRangeNdayOfTime(end_time, -7);
        }else if(StringUtil.isEmpty(end_time)){
            end_time = DateUtils.getRangeNdayOfTime(start_time, 7);
        }
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            format.parse(start_time);
            format.parse(end_time);
        } catch (ParseException e) {
            log.error("date format error: ", e);
            return BaseResultGenerator.failure(500, e.toString());
        }
        return systemService.dailyTrendOfStorage(start_time, end_time);
    }
}
