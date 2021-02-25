package com.mesalab.qgw.benchmark.clickhouse;

import cn.hutool.core.io.file.FileReader;
import cn.hutool.core.io.file.FileWriter;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayway.jsonpath.JsonPath;
import com.mesalab.qgw.benchmark.Writer;
import com.zdjizhi.utils.DateUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;


public class ClickhouseWriter extends Writer {
    private static final Log log = LogFactory.get();
    public String buildPocSQL() {
        String pocSql = "";
        try {
            Map<String, List<String>>  environment = getFilterOptions(new File("").getCanonicalPath()
                    + File.separator + "benchmark" + File.separator + "ck_filter.json");

            FileReader templateReader = new FileReader(new File("").getCanonicalPath()
                    + File.separator + "benchmark" + File.separator + "ck_queries_template.sql");

            FileWriter writer = new FileWriter(new File("").getCanonicalPath()
                    + File.separator + "benchmark" + File.separator + "ck_queries" + "_"
                    + DateUtils.getCurrentDate(DateUtils.YYYYMMDD) +  ".sql");

            if (writer.getFile().exists()) {
                return writer.getFile().getAbsolutePath();
            }

            List<String> rawLines = templateReader.readLines();
            List<String> resultLines = Lists.newArrayList();
            rawLines.stream().forEach(line -> {

                if (line.contains(TITLE_PREFIX)) {
                    if(IS_TITLE) {
                        resultLines.add(line);
                    }
                } else {

                    if (line.contains(LABEL_COMMON_FILTER)) {
                        for (String filter : environment.get(LABEL_COMMON_FILTER)) {
                            String replaceLine = line.replaceAll(LABEL_TIME_START, environment.get(LABEL_TIME_START).get(0))
                                    .replaceAll(LABEL_TIME_END, environment.get(LABEL_TIME_END).get(0))
                                    .replaceAll(LABEL_COMMON_FILTER, filter);

                            resultLines.add(replaceLine);

                        }

                    } else if (line.contains(LABEL_INDEX_FILTER)){
                        for (String filter : environment.get(LABEL_INDEX_FILTER)) {
                            String replaceLine = line.replaceAll(LABEL_TIME_START, environment.get(LABEL_TIME_START).get(0))
                                    .replaceAll(LABEL_TIME_END, environment.get(LABEL_TIME_END).get(0))
                                    .replaceAll(LABEL_INDEX_FILTER, filter);

                            resultLines.add(replaceLine);

                        }

                    } else {
                        resultLines.add(
                                line.replaceAll(LABEL_TIME_START, environment.get(LABEL_TIME_START).get(0))
                                        .replaceAll(LABEL_TIME_END, environment.get(LABEL_TIME_END).get(0)));
                    }



                }

            });
            writer.appendLines(resultLines);
            resultLines.clear();
            pocSql = writer.getFile().getAbsolutePath();
        } catch (IOException e) {
            log.error("Not found file {}", e);
        }
        return  pocSql;
    }



}
