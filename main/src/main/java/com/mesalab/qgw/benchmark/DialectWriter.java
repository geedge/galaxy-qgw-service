package com.mesalab.qgw.benchmark;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileWriter;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.google.common.collect.Lists;
import com.mesalab.common.nacos.NacosConfig;
import com.geedgenetworks.utils.DateUtils;
import com.mesalab.services.service.SQLDatasetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class DialectWriter extends Writer {

    private static final Log log = LogFactory.get();
    private static final String EXECUTE_ENGINE= "execute_engine";
    private static final String SQL_TEXT= "sql_text";

    @Autowired
    private NacosConfig nacosConfig;

    @Autowired
    private ConfigService systemConfigService;
    @Autowired
    private SQLDatasetService sqlDatasetService;

    @Override
    public String buildPocSQL(List<String> dialectList, String dialect, String category) {
        String pocSql = "";
        try {
            String path = new File("").getCanonicalPath()
                    + File.separator + "benchmark" + File.separator + dialect + "_queries" + "_"
                    + DateUtils.getCurrentDate(DateUtils.YYYYMMDD) + ".sql";
            if (CollectionUtil.isEmpty(dialectList) && !Files.exists(Paths.get(path))) {
                Map<String, Object> datasetResult = sqlDatasetService.getDatasets(Lists.newArrayList(), category, null);
                ArrayList<Map<String, String>> datasetList = (ArrayList<Map<String, String>>) datasetResult.get("list");
                dialectList = Lists.newLinkedList();
                if (CollectionUtil.isNotEmpty(datasetList)) {
                    for (int i = 0; i < datasetList.size(); i++) {
                        if (datasetList.get(i).get(EXECUTE_ENGINE).equalsIgnoreCase(dialect)) {
                            dialectList.add(datasetList.get(i).get(SQL_TEXT));
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
