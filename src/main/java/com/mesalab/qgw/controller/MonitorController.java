package com.mesalab.qgw.controller;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.*;

/**
 * @author wangwei
 * @description:
 * @date 2020/7/31 1:59 下午
 */
@RestController
@RequestMapping(value = "/monitor")
public class MonitorController {
    private static final Log log = LogFactory.get();
    private static final String VERSION_FILE = "config" + File.separator +  "version.json";

    @RequestMapping("/info")
    public String config() {
        String jsonStr = "";
        Reader reader = null;
        try {
            String pathRoot = new File("").getCanonicalPath();
            File jsonFile = new File(pathRoot + File.separator + VERSION_FILE);
            reader = new InputStreamReader(new FileInputStream(jsonFile), "utf-8");
            int ch;
            StringBuffer sb = new StringBuffer();
            while ((ch = reader.read()) != -1) {
                sb.append((char) ch);
            }
            jsonStr = sb.toString();
        } catch (IOException e) {
            log.error("Get file error: ", e);
        } finally {

            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    log.error("Close Reader error: ", e);
                }
            }
        }
        return jsonStr;
    }
}
