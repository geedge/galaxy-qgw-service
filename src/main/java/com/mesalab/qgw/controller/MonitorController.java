package com.mesalab.qgw.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.*;

/**
 * @author wangwei
 * @description:
 * @date 2020/7/31 1:59 下午
 */
@Slf4j
@Api(value = "/monitor", tags = "监控")
@RestController
@RequestMapping(value = "/monitor")
public class MonitorController {

    @ApiOperation(value = "配置中心", httpMethod = "GET", notes = "获取数据平台应用列表")
    @RequestMapping("/info")
    public String config() {
        String jsonStr = "";
        try {
            String pathRoot = new File("").getCanonicalPath();
            File jsonFile = new File(pathRoot + "/config/version.json");
            FileReader fileReader = new FileReader(jsonFile);
            Reader reader = new InputStreamReader(new FileInputStream(jsonFile), "utf-8");
            int ch;
            StringBuffer sb = new StringBuffer();
            while ((ch = reader.read()) != -1) {
                sb.append((char) ch);
            }
            fileReader.close();
            reader.close();
            jsonStr = sb.toString();
        } catch (IOException e) {
            log.error("get file error: ", e);
        }
        return jsonStr;
    }
}
