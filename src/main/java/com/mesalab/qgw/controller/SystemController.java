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
        String start_time = StringUtil.trim((String) param.get("start_time"));
        String end_time = StringUtil.trim((String) param.get("end_time"));
        String format = DateUtils.YYYY_MM_DD_HH24_MM_SS;
        if(StringUtil.isEmpty(start_time) && StringUtil.isEmpty(end_time)){
            Date date = new Date();
            start_time = DateUtils.getFormatDate(DateUtils.getSomeDate(date, -7), format);
            end_time = DateUtils.getFormatDate(date, format);
        }else if(StringUtil.isEmpty(start_time)){
            Date date = DateUtils.convertStringToDate(end_time, format);
            start_time = DateUtils.getFormatDate(DateUtils.getSomeDate(date, -7), format);
        }else if(StringUtil.isEmpty(end_time)){
            Date date = DateUtils.convertStringToDate(start_time, format);
            end_time = DateUtils.getFormatDate(DateUtils.getSomeDate(date, 7), format);
        }
        try {
            SimpleDateFormat parseFormat = new SimpleDateFormat(format);
            parseFormat.parse(start_time);
            parseFormat.parse(end_time);
        } catch (ParseException e) {
            log.error("date format error: ", e);
            return BaseResultGenerator.failure(500, e.toString());
        }
        return systemService.dailyTrendOfStorage(start_time, end_time);
    }
}
