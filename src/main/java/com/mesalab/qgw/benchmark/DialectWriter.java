package com.mesalab.qgw.benchmark;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileWriter;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson2.JSON;
import com.google.common.collect.Lists;
import com.geedgenetworks.utils.DateUtils;
import com.mesalab.qgw.service.DatasetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@Component
public class DialectWriter extends Writer {

    private static final Log log = LogFactory.get();
    private static final String BACKEND_ENGINE= "backend_engine";
    private static final String TEMPLATE= "template";
    @Autowired
    DatasetService datasetService;

    @Override
    public String buildPocSQL(List<String> dialectList, String dialect, String category) {
        String pocSql = "";
        try {
            String path = new File("").getCanonicalPath()
                    + File.separator + "benchmark" + File.separator + dialect + "_queries" + "_"
                    + DateUtils.getCurrentDate(DateUtils.YYYYMMDD) + ".sql";
            if (CollectionUtil.isEmpty(dialectList) && !Files.exists(Paths.get(path))) {
                List<Map<String, Object>> datasetList = datasetService.getDatasets(Lists.newArrayList(), category, null);
                dialectList = Lists.newLinkedList();
                if (CollectionUtil.isNotEmpty(datasetList)) {
                    for (int i = 0; i < datasetList.size(); i++) {
                        if (String.valueOf(datasetList.get(i).get(BACKEND_ENGINE)).equalsIgnoreCase(dialect)) {
                            Map map = JSON.parseObject(String.valueOf(datasetList.get(i).get(TEMPLATE)), Map.class);
                            dialectList.add(String.valueOf(map.get("statement")));
                        }
                    }
                }
            }
            File file = FileUtil.writeBytes("".getBytes(), path);
            FileWriter writer = FileWriter.create(file);
            pocSql = generatePocSQL(dialectList, writer);
        } catch (IOException e) {
            log.error("Not found file {}", e);
        }
        return pocSql;
    }

}
