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
    public String buildPocSQL(String category,String dialect) {
        String pocSql = "";
        Map<String, Object> statisticsRule = sqlDatasetService.getDatasets(Lists.newArrayList(), category, null);
        ArrayList<Map<String, String>> list1 = (ArrayList<Map<String, String>>) statisticsRule.get("list");
        List<String> dialectList = Lists.newLinkedList();
        try {
            if (CollectionUtil.isNotEmpty(list1)){
                for (int i = 0; i < list1.size(); i++) {
                    if (list1.get(i).get(EXECUTE_ENGINE).equalsIgnoreCase(dialect)){
                        dialectList.add(list1.get(i).get(SQL_TEXT));
                    }
                }
            }

            File file = FileUtil.writeBytes("".getBytes(), new File("").getCanonicalPath()
                    + File.separator + "benchmark" + File.separator + dialect + "_queries" + "_"
                    + DateUtils.getCurrentDate(DateUtils.YYYYMMDD) + ".sql");
            FileWriter writer = FileWriter.create(file);

            pocSql = generatePocSQL(dialectList, writer);
        } catch (IOException e) {
            log.error("Not found file {}", e);
        }
        return pocSql;
    }

}
