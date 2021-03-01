package com.mesalab.qgw.benchmark.druid;

import cn.hutool.core.io.file.FileReader;
import cn.hutool.core.io.file.FileWriter;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.collect.Lists;
import com.mesalab.qgw.benchmark.Writer;
import com.zdjizhi.utils.DateUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class DruidWriter extends Writer {
    private static final Log log = LogFactory.get();

    @Override
    public String buildPocSQL() {
        String pocSql = "";
        try {
            Map<String, List<String>> environment = getFilterOptions(new File("").getCanonicalPath()
                    + File.separator + "benchmark" + File.separator + "druid_filter.json");

            FileReader templateReader = new FileReader(new File("").getCanonicalPath()
                    + File.separator + "benchmark" + File.separator + "druid_queries_template.sql");

            FileWriter druidWriter = new FileWriter(new File("").getCanonicalPath()
                    + File.separator + "benchmark" + File.separator + "druid_queries" + "_"
                    + DateUtils.getCurrentDate(DateUtils.YYYYMMDD) +  ".sql");

            if (druidWriter.getFile().exists()) {
                return druidWriter.getFile().getAbsolutePath();
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
            druidWriter.appendLines(resultLines);
            resultLines.clear();
            pocSql = druidWriter.getFile().getAbsolutePath();
        } catch (IOException e) {
            log.error("Not found file {}", e);
        }
        return  pocSql;
    }
}
