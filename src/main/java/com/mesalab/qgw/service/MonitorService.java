package com.mesalab.qgw.service;


import com.alibaba.fastjson.JSONObject;

/**
 * @Auther: lijinyang
 * @Date: 2023/04/23/11:17
 */
public interface MonitorService {

    /**
     * 获取版本信息
     * @return
     */
    JSONObject getVersionInfo();
}
