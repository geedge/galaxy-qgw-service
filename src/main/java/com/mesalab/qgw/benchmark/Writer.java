package com.mesalab.qgw.benchmark;

import cn.hutool.core.io.IORuntimeException;
import cn.hutool.core.io.file.FileWriter;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson2.JSON;
import com.google.common.collect.Lists;
import com.mesalab.common.nacos.NacosConfig;
import com.mesalab.common.nacos.NacosConst;
import com.geedgenetworks.utils.StringUtil;
import com.mesalab.qgw.service.DatabaseService;
import com.mesalab.qgw.service.DatasetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;

@Component
public abstract class Writer {

    @Autowired
    NacosConfig nacosConfig;
    @Autowired
    DatabaseService databaseService;
    @Autowired
    DatasetService datasetService;

    private static final Log log = LogFactory.get();


    protected String generatePocSQL(List<String> list, FileWriter writer) {
        String pocSQL = "";
        try {
            File file = writer.getFile();
            if (file.exists()) {
                if (!file.createNewFile()) {
                    log.error("create new File error.");
                    throw new IOException("create new File error.");
                }

            }
            List<LinkedHashMap> variables = datasetService.getVariable();
            List<String> resultLines = Lists.newArrayList();
            for (String line : list) {
                String sql = datasetService.buildExecDSL(variables, line);
                resultLines.add(sql);
            }
            writer.appendLines(resultLines);
            resultLines.clear();
            pocSQL = writer.getFile().getAbsolutePath();
        } catch (IORuntimeException | IOException e) {
            log.error("Generate PocSQL Exception:{}", e);
        }
        return pocSQL;
    }

    protected abstract String buildPocSQL(List<String> dialectList, String dialect, String category);


}
